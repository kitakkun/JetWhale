package com.kitakkun.jetwhale.plugins.background.agent

import android.app.ActivityManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class PlatformDefaultsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [33])
    fun `a namespaced job id is refused before Android 14 instead of cancelling the job with that number`() {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.schedule(JobInfo.Builder(42, ComponentName(context, "com.example.SyncJobService")).setRequiresCharging(true).build())

        assertFailsWith<IllegalArgumentException> { runBlocking { source("JobScheduler").cancel(CancelTarget.ById("other/42")) } }

        assertNotNull(scheduler.getPendingJob(42))
    }

    @Test
    @Config(sdk = [34])
    fun `a running service reports the same start time on every read`() {
        val upload = ActivityManager.RunningServiceInfo().apply {
            service = ComponentName(context, "com.example.UploadService")
            uid = Process.myUid()
            activeSince = SystemClock.elapsedRealtime()
        }
        shadowOf(context.getSystemService(ActivityManager::class.java)).setServices(listOf(upload))
        val services = source("Services")

        val first = runBlocking { services.observe().first() }
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_500))
        val second = runBlocking { services.observe().first() }

        assertEquals(first, second)
    }

    private fun source(name: String): BackgroundWorkSource = BackgroundWorkSource.platformDefaults().single { it.info.name == name }
}
