package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkSnapshot
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkOperationResult
import com.kitakkun.jetwhale.plugins.background.protocol.WorkSourceInfo
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState

/** An app whose background work is [items], answering the way the agent plugin does. */
internal class FakeBackgroundWorkClient(
    var items: List<BackgroundWorkItem>,
) : BackgroundWorkClient {
    val cancelled = mutableListOf<Pair<String, CancelTarget>>()
    val ranNow = mutableListOf<Pair<String, String>>()

    override suspend fun snapshot(): BackgroundWorkSnapshot = BackgroundWorkSnapshot(
        sources = items.map(BackgroundWorkItem::source).distinct().map {
            WorkSourceInfo(name = it, available = true, unavailableReason = null, supportsCancelByTag = true, supportsCancelByUniqueName = true)
        },
        items = items,
    )

    override suspend fun cancel(source: String, target: CancelTarget): WorkOperationResult {
        cancelled += source to target
        if (target is CancelTarget.ById) items = items.map { if (it.source == source && it.id == target.id) it.copy(state = WorkState.Cancelled) else it }
        return WorkOperationResult(message = "Cancelled.", error = null)
    }

    override suspend fun runNow(source: String, id: String): WorkOperationResult {
        ranNow += source to id
        val item = items.firstOrNull { it.source == source && it.id == id }
            ?: return WorkOperationResult(message = null, error = "no work has id $id")
        return if (item.canRunNow) WorkOperationResult(message = "Enqueued a copy.", error = null) else WorkOperationResult(message = null, error = "cannot run $id now")
    }
}

internal fun workItem(source: String, id: String, state: WorkState, tags: List<String>, canRunNow: Boolean): BackgroundWorkItem = BackgroundWorkItem(
    source = source,
    id = id,
    name = "com.example.app.${id.replaceFirstChar(Char::uppercase)}Worker",
    state = state,
    tags = tags,
    uniqueName = null,
    runAttemptCount = 0,
    constraints = emptyList(),
    nextRunEpochMillis = null,
    periodMillis = null,
    flexMillis = null,
    progress = emptyMap(),
    output = emptyMap(),
    stopReason = null,
    details = emptyMap(),
    canCancel = true,
    canRunNow = canRunNow,
    runNowHint = null,
)
