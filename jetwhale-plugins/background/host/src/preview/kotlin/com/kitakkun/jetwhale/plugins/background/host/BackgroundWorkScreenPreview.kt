package com.kitakkun.jetwhale.plugins.background.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkSnapshot
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkSourceInfo
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState

private val previewSources = listOf(
    WorkSourceInfo(name = "WorkManager", available = true, unavailableReason = null, supportsCancelByTag = true, supportsCancelByUniqueName = true),
    WorkSourceInfo(name = "JobScheduler", available = true, unavailableReason = null, supportsCancelByTag = false, supportsCancelByUniqueName = false),
)

private val previewSync = BackgroundWorkItem(
    source = "WorkManager",
    id = "0b1c9f3e-5d2a-4c1e-9f7a-2e8d6b4a1c30",
    name = "com.example.app.sync.SyncWorker",
    state = WorkState.Enqueued,
    tags = listOf("sync"),
    uniqueName = null,
    runAttemptCount = 2,
    constraints = listOf("network: UNMETERED", "charging"),
    nextRunEpochMillis = 1_790_000_000_000,
    periodMillis = 900_000,
    flexMillis = 300_000,
    progress = emptyMap(),
    output = emptyMap(),
    stopReason = "connectivity constraint no longer met",
    details = mapOf("Generation" to "0"),
    canCancel = true,
    canRunNow = true,
    runNowHint = null,
)

private val previewFailed = previewSync.copy(
    id = "7f3e2d1c-0b9a-4e8d-8c7b-6a5f4e3d2c1b",
    name = "com.example.app.upload.UploadWorker",
    state = WorkState.Failed,
    tags = listOf("upload"),
    periodMillis = null,
    flexMillis = null,
    output = mapOf("reason" to "HTTP 503"),
    canCancel = false,
)

private val previewJob = previewSync.copy(
    source = "JobScheduler",
    id = "42",
    name = "com.example.app.CleanupJobService",
    state = WorkState.Scheduled,
    tags = emptyList(),
    runAttemptCount = null,
    canRunNow = false,
    runNowHint = "adb shell cmd jobscheduler run -f com.example.app 42",
)

private object NoActions : BackgroundWorkActions {
    override fun refresh() = Unit

    override fun select(key: WorkKey) = Unit

    override fun cancel(source: String, target: CancelTarget) = Unit

    override fun runNow(key: WorkKey) = Unit

    override fun changeFilter(filter: WorkFilter) = Unit
}

@Preview
@Composable
private fun BackgroundWorkScreenPreview() {
    JwTheme(darkTheme = false) {
        BackgroundWorkScreen(
            snapshot = BackgroundWorkSnapshot(sources = previewSources, items = listOf(previewSync, previewFailed, previewJob)),
            visibleItems = listOf(previewSync, previewFailed, previewJob),
            selectedItem = previewSync,
            history = mapOf(previewSync.key to listOf(StateTransition(WorkState.Running, 1_790_000_000_000), StateTransition(WorkState.Enqueued, 1_790_000_060_000))),
            filter = WorkFilter(states = emptySet(), query = ""),
            status = WorkStatus(message = "Enqueued a one-time copy of SyncWorker.", isError = false),
            actions = NoActions,
        )
    }
}

@Preview
@Composable
private fun BackgroundWorkScreenNoSourcesPreview() {
    JwTheme(darkTheme = true) {
        BackgroundWorkScreen(
            snapshot = BackgroundWorkSnapshot(sources = emptyList(), items = emptyList()),
            visibleItems = emptyList(),
            selectedItem = null,
            history = emptyMap(),
            filter = WorkFilter(states = emptySet(), query = ""),
            status = null,
            actions = NoActions,
        )
    }
}

@Preview
@Composable
private fun WorkTablePreview() {
    JwTheme(darkTheme = false) {
        WorkTable(items = listOf(previewSync, previewFailed, previewJob), selectedKey = previewJob.key, onSelect = {})
    }
}

@Preview
@Composable
private fun WorkDetailPreview() {
    JwTheme(darkTheme = true) {
        WorkDetail(item = previewJob, source = previewSources[1], history = emptyList(), actions = NoActions)
    }
}

@Preview
@Composable
private fun StateTagPreview() {
    JwTheme(darkTheme = false) {
        StateTag(WorkState.Running)
    }
}

@Preview
@Composable
private fun ConfirmDialogPreview() {
    JwTheme(darkTheme = false) {
        ConfirmDialog(title = "Cancel all WorkManager work tagged sync?", message = "It cannot be undone.", confirmLabel = "Cancel work", onConfirm = {}, onDismiss = {})
    }
}

@Preview
@Composable
private fun CancelUniqueWorkDialogPreview() {
    JwTheme(darkTheme = false) {
        CancelUniqueWorkDialog(sourceNames = listOf("WorkManager"), onCancel = { _, _ -> }, onDismiss = {})
    }
}
