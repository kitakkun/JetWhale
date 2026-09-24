package com.kitakkun.jetwhale.plugins.background.agent.workmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.kitakkun.jetwhale.plugins.background.agent.BackgroundWorkSource
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkManagerSourceTest {
    private lateinit var workManager: WorkManager
    private lateinit var source: BackgroundWorkSource

    // Charging is never met under the test driver, so work that needs it stays enqueued until a
    // test acts on it.
    private val needsCharging = Constraints.Builder().setRequiresCharging(true).build()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        workManager = WorkManager.getInstance(context)
        source = BackgroundWorkSource.workManager(workManager)
    }

    @Test
    fun `enqueued work is listed with its worker class tags and constraints`() {
        val request = OneTimeWorkRequest.Builder(SucceedingWorker::class.java).setConstraints(needsCharging).addTag("sync").build()
        workManager.enqueue(request).result.get()

        val item = items().single { it.id == request.id.toString() }

        assertEquals(SucceedingWorker::class.java.name, item.name)
        assertEquals(WorkState.Enqueued, item.state)
        assertEquals(listOf("sync"), item.tags)
        assertEquals(listOf("charging"), item.constraints)
        assertTrue(item.canCancel)
        assertTrue(item.canRunNow)
    }

    @Test
    fun `periodic work carries its interval`() {
        val request = PeriodicWorkRequest.Builder(SucceedingWorker::class.java, 1, TimeUnit.HOURS).setConstraints(needsCharging).build()
        workManager.enqueue(request).result.get()

        assertEquals(TimeUnit.HOURS.toMillis(1), items().single { it.id == request.id.toString() }.periodMillis)
    }

    @Test
    fun `cancelling by id cancels only that work`() {
        val kept = enqueueWaiting("kept")
        val cancelled = enqueueWaiting("cancelled")

        runBlocking { source.cancel(CancelTarget.ById(cancelled.toString())) }

        assertEquals(WorkState.Cancelled, stateOf(cancelled))
        assertEquals(WorkState.Enqueued, stateOf(kept))
    }

    @Test
    fun `cancelling by tag cancels every work with that tag`() {
        val first = enqueueWaiting("batch")
        val second = enqueueWaiting("batch")
        val other = enqueueWaiting("other")

        runBlocking { source.cancel(CancelTarget.ByTag("batch")) }

        assertEquals(listOf(WorkState.Cancelled, WorkState.Cancelled, WorkState.Enqueued), listOf(first, second, other).map(::stateOf))
    }

    @Test
    fun `cancelling by unique name cancels that unique work`() {
        val request = OneTimeWorkRequest.Builder(SucceedingWorker::class.java).setConstraints(needsCharging).build()
        workManager.enqueueUniqueWork("nightly", ExistingWorkPolicy.REPLACE, request).result.get()

        runBlocking { source.cancel(CancelTarget.ByUniqueName("nightly")) }

        assertEquals(WorkState.Cancelled, stateOf(request.id))
    }

    @Test
    fun `run now enqueues a copy that runs while the original keeps waiting`() {
        val original = enqueueWaiting("sync")

        runBlocking { source.runNow(original.toString()) }

        val copy = items().single { it.id != original.toString() }
        assertEquals(WorkState.Succeeded, copy.state)
        assertEquals(SucceedingWorker::class.java.name, copy.name)
        assertTrue(RUN_NOW_TAG in copy.tags)
        assertEquals(WorkState.Enqueued, stateOf(original))
    }

    @Test
    fun `failed work reports its output data`() {
        val request = OneTimeWorkRequest.Builder(FailingWorker::class.java).build()
        workManager.enqueue(request).result.get()

        val item = items().single { it.id == request.id.toString() }

        assertEquals(WorkState.Failed, item.state)
        assertEquals(mapOf("reason" to "demo failure"), item.output)
        assertEquals(false, item.canCancel)
    }

    @Test
    fun `array values in output data are shown element by element`() {
        val request = OneTimeWorkRequest.Builder(ArrayOutputWorker::class.java).build()
        workManager.enqueue(request).result.get()

        assertEquals(mapOf("counts" to "1, 2, 3"), items().single { it.id == request.id.toString() }.output)
    }

    @Test
    fun `an id that is not a work id is refused`() {
        assertFailsWith<IllegalArgumentException> { runBlocking { source.cancel(CancelTarget.ById("not-a-uuid")) } }
    }

    @Test
    fun `work whose worker class cannot be loaded is shown by id and cannot be run now`() {
        val info = WorkInfo(
            id = UUID.randomUUID(),
            state = WorkInfo.State.ENQUEUED,
            tags = setOf("com.example.GoneWorker", "sync"),
            outputData = Data.EMPTY,
            progress = Data.EMPTY,
            runAttemptCount = 0,
            generation = 0,
            constraints = Constraints.NONE,
            initialDelayMillis = 0,
            periodicityInfo = null,
            nextScheduleTimeMillis = Long.MAX_VALUE,
            stopReason = WorkInfo.STOP_REASON_NOT_STOPPED,
        )

        val item = workInfoToItem(info) { false }

        assertEquals(info.id.toString(), item.name)
        assertEquals(listOf("com.example.GoneWorker", "sync"), item.tags)
        assertEquals(false, item.canRunNow)
        assertEquals(null, item.nextRunEpochMillis)
    }

    private fun enqueueWaiting(tag: String): UUID {
        val request = OneTimeWorkRequest.Builder(SucceedingWorker::class.java).setConstraints(needsCharging).addTag(tag).build()
        workManager.enqueue(request).result.get()
        return request.id
    }

    private fun items(): List<BackgroundWorkItem> = runBlocking { source.observe().first() }

    private fun stateOf(id: UUID): WorkState = items().single { it.id == id.toString() }.state
}

class SucceedingWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.success()
}

class FailingWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.failure(Data.Builder().putString("reason", "demo failure").build())
}

class ArrayOutputWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.success(Data.Builder().putIntArray("counts", intArrayOf(1, 2, 3)).build())
}
