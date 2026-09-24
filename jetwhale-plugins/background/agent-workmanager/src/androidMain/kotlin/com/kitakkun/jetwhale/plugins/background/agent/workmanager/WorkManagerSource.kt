package com.kitakkun.jetwhale.plugins.background.agent.workmanager

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.await
import com.kitakkun.jetwhale.plugins.background.agent.BackgroundWorkSource
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkSourceInfo
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * The work [workManager] holds, live, as a [BackgroundWorkSource]: every state, including finished
 * work until WorkManager prunes it.
 *
 * ```kotlin
 * JetWhaleBackgroundWorkAgentPlugin(
 *     sources = { BackgroundWorkSource.platformDefaults() + BackgroundWorkSource.workManager(WorkManager.getInstance(context)) },
 * )
 * ```
 */
fun BackgroundWorkSource.Companion.workManager(workManager: WorkManager): BackgroundWorkSource = WorkManagerSource(workManager)

private const val SOURCE_NAME = "WorkManager"

private class WorkManagerSource(private val workManager: WorkManager) : BackgroundWorkSource {
    override val info = WorkSourceInfo(
        name = SOURCE_NAME,
        available = true,
        unavailableReason = null,
        supportsCancelByTag = true,
        supportsCancelByUniqueName = true,
    )

    override fun observe(): Flow<List<BackgroundWorkItem>> = workManager.getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.entries)).map { infos ->
        infos.map { workInfoToItem(it, ::isWorkerClass) }
    }

    override suspend fun cancel(target: CancelTarget): String {
        when (target) {
            is CancelTarget.ById -> workManager.cancelWorkById(parseWorkId(target.id)).await()
            is CancelTarget.ByTag -> workManager.cancelAllWorkByTag(target.tag).await()
            is CancelTarget.ByUniqueName -> workManager.cancelUniqueWork(target.uniqueName).await()
        }
        return when (target) {
            is CancelTarget.ById -> "Cancelled work ${target.id}."
            is CancelTarget.ByTag -> "Cancelled all work tagged ${target.tag}."
            is CancelTarget.ByUniqueName -> "Cancelled the unique work ${target.uniqueName}."
        }
    }

    /**
     * WorkManager has no way to start enqueued work early, so this enqueues a one-time copy with no
     * constraints and no delay. WorkInfo does not expose the original input, so the copy runs with
     * empty input data.
     */
    override suspend fun runNow(id: String): String {
        val info = workManager.getWorkInfoByIdFlow(parseWorkId(id)).first()
            ?: throw IllegalArgumentException("no work has id $id")
        val workerClassName = info.tags.firstOrNull(::isWorkerClass)
            ?: throw IllegalStateException("the worker class of $id cannot be loaded, so it cannot be copied")

        @Suppress("UNCHECKED_CAST")
        val workerClass = Class.forName(workerClassName) as Class<out ListenableWorker>
        val copy = OneTimeWorkRequest.Builder(workerClass)
            .apply { info.tags.filterNot { it == workerClassName }.forEach(::addTag) }
            .addTag(RUN_NOW_TAG)
            .build()
        workManager.enqueue(copy).await()
        return "Enqueued ${copy.id}, a one-time copy of $id with no constraints or delay and empty input data."
    }
}

/** Tags a copy [BackgroundWorkSource.runNow] enqueued, so the host can tell it from the original. */
internal const val RUN_NOW_TAG = "jetwhale-run-now"

private fun parseWorkId(id: String): UUID = try {
    UUID.fromString(id)
} catch (e: IllegalArgumentException) {
    throw IllegalArgumentException("'$id' is not a WorkManager work id", e)
}

/** WorkManager tags every request with its worker's class name; this finds that tag. */
private fun isWorkerClass(tag: String): Boolean = try {
    ListenableWorker::class.java.isAssignableFrom(Class.forName(tag))
} catch (_: ClassNotFoundException) {
    false
} catch (_: LinkageError) {
    false
}

/**
 * [info] as the host shows it. [isWorkerClass] picks the tag WorkManager added for the worker
 * class, which WorkInfo does not otherwise expose.
 */
internal fun workInfoToItem(info: WorkInfo, isWorkerClass: (String) -> Boolean): BackgroundWorkItem {
    val workerClass = info.tags.firstOrNull(isWorkerClass)
    val periodicity = info.periodicityInfo
    return BackgroundWorkItem(
        source = SOURCE_NAME,
        id = info.id.toString(),
        name = workerClass ?: info.id.toString(),
        state = workStateOf(info.state),
        tags = info.tags.filterNot { it == workerClass }.sorted(),
        // WorkInfo does not say which unique name, if any, the work was enqueued under.
        uniqueName = null,
        runAttemptCount = info.runAttemptCount,
        constraints = constraintsOf(info.constraints),
        nextRunEpochMillis = info.nextScheduleTimeMillis.takeIf { it != Long.MAX_VALUE && !info.state.isFinished },
        periodMillis = periodicity?.repeatIntervalMillis,
        flexMillis = periodicity?.flexIntervalMillis,
        progress = dataOf(info.progress),
        output = dataOf(info.outputData),
        stopReason = stopReasonOf(info.stopReason),
        details = buildMap {
            put("Generation", info.generation.toString())
            if (info.initialDelayMillis > 0) put("Initial delay", "${info.initialDelayMillis} ms")
            if (RUN_NOW_TAG in info.tags) put("Enqueued by", "JetWhale run now")
        },
        canCancel = !info.state.isFinished,
        canRunNow = workerClass != null,
        runNowHint = null,
    )
}

private fun workStateOf(state: WorkInfo.State): WorkState = when (state) {
    WorkInfo.State.ENQUEUED -> WorkState.Enqueued
    WorkInfo.State.RUNNING -> WorkState.Running
    WorkInfo.State.SUCCEEDED -> WorkState.Succeeded
    WorkInfo.State.FAILED -> WorkState.Failed
    WorkInfo.State.BLOCKED -> WorkState.Blocked
    WorkInfo.State.CANCELLED -> WorkState.Cancelled
}

private fun constraintsOf(constraints: Constraints): List<String> = buildList {
    if (constraints.requiredNetworkType != NetworkType.NOT_REQUIRED) add("network: ${constraints.requiredNetworkType}")
    if (constraints.requiresCharging()) add("charging")
    if (constraints.requiresBatteryNotLow()) add("battery not low")
    if (constraints.requiresDeviceIdle()) add("device idle")
    if (constraints.requiresStorageNotLow()) add("storage not low")
    if (constraints.hasContentUriTriggers()) add("content URI triggers")
}

private fun dataOf(data: Data): Map<String, String> = data.keyValueMap.mapValues { (_, value) ->
    when (value) {
        is Array<*> -> value.joinToString()
        else -> value.toString()
    }
}

private fun stopReasonOf(reason: Int): String? = when (reason) {
    WorkInfo.STOP_REASON_NOT_STOPPED -> null
    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "cancelled by the app"
    WorkInfo.STOP_REASON_PREEMPT -> "preempted"
    WorkInfo.STOP_REASON_TIMEOUT -> "timed out"
    WorkInfo.STOP_REASON_DEVICE_STATE -> "device state"
    WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "battery-not-low constraint no longer met"
    WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "charging constraint no longer met"
    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "connectivity constraint no longer met"
    WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "device-idle constraint no longer met"
    WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "storage-not-low constraint no longer met"
    WorkInfo.STOP_REASON_QUOTA -> "quota exhausted"
    WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "background restriction"
    WorkInfo.STOP_REASON_APP_STANDBY -> "app standby"
    WorkInfo.STOP_REASON_USER -> "stopped by the user"
    WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "system processing"
    WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "estimated launch time changed"
    WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT -> "foreground service timed out"
    else -> "unknown ($reason)"
}
