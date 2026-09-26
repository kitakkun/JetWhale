package com.kitakkun.jetwhale.plugins.background.agent

import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkSourceInfo
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume

actual fun BackgroundWorkSource.Companion.platformDefaults(): List<BackgroundWorkSource> = listOf(BackgroundTaskSource)

private object BackgroundTaskSource : BackgroundWorkSource {
    override val info = WorkSourceInfo(
        name = "BGTaskScheduler",
        available = true,
        unavailableReason = null,
        supportsCancelByTag = false,
        supportsCancelByUniqueName = false,
    )

    override fun observe(): Flow<List<BackgroundWorkItem>> = pollWork(POLL_INTERVAL_MILLIS) {
        pendingRequests().map(::toItem)
    }

    private suspend fun pendingRequests(): List<BGTaskRequest> = suspendCancellableCoroutine { continuation ->
        BGTaskScheduler.sharedScheduler.getPendingTaskRequestsWithCompletionHandler { requests ->
            continuation.resume(requests.orEmpty().filterIsInstance<BGTaskRequest>())
        }
    }

    private fun toItem(request: BGTaskRequest): BackgroundWorkItem = BackgroundWorkItem(
        source = info.name,
        id = request.identifier,
        name = request.identifier,
        state = WorkState.Scheduled,
        tags = emptyList(),
        uniqueName = null,
        runAttemptCount = null,
        constraints = buildList {
            if (request is BGProcessingTaskRequest) {
                if (request.requiresNetworkConnectivity) add("network required")
                if (request.requiresExternalPower) add("external power")
            }
        },
        nextRunEpochMillis = request.earliestBeginDate?.let { (it.timeIntervalSince1970 * 1000).toLong() },
        periodMillis = null,
        flexMillis = null,
        progress = emptyMap(),
        output = emptyMap(),
        stopReason = null,
        details = mapOf(
            "Kind" to when (request) {
                is BGAppRefreshTaskRequest -> "App refresh"
                is BGProcessingTaskRequest -> "Processing"
                else -> request::class.simpleName ?: "Task request"
            },
        ),
        canCancel = true,
        canRunNow = false,
        // The only way to launch a scheduled task early is the debugger's private selector; the
        // agent shows the command instead of calling private API itself.
        runNowHint = "e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@\"${request.identifier}\"]",
    )

    override suspend fun cancel(target: CancelTarget): String {
        val identifier = (target as? CancelTarget.ById)?.id ?: throw IllegalArgumentException("task requests are cancelled by their identifier")
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(identifier)
        return "Cancelled the task request $identifier."
    }

    override suspend fun runNow(id: String): String = throw UnsupportedOperationException("iOS launches a task only from the debugger; pause in Xcode and run the lldb command shown for it")
}
