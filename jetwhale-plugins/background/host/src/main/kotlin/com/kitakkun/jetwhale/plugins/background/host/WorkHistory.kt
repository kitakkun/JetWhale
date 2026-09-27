package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState

/** Identifies one piece of work across snapshots: ids are unique only within their source. */
internal data class WorkKey(val source: String, val id: String)

internal val BackgroundWorkItem.key: WorkKey get() = WorkKey(source, id)

/** A state the host saw a piece of work enter, and when. */
internal data class StateTransition(val state: WorkState, val atEpochMillis: Long)

/** How many transitions are kept per piece of work; periodic work cycles through states forever. */
internal const val MAX_TRANSITIONS_PER_WORK = 50

/** How many pieces of work that are gone from the app keep their history; an app can create work endlessly. */
internal const val MAX_DEPARTED_WORK = 500

/**
 * [history] with the states in [items] appended wherever they differ from the last one recorded,
 * stamped [nowEpochMillis]. Work that is gone from [items] keeps its history, so a pruned or
 * cancelled job can still be looked up — the [MAX_DEPARTED_WORK] most recently changed of them.
 */
internal fun recordTransitions(
    history: Map<WorkKey, List<StateTransition>>,
    items: List<BackgroundWorkItem>,
    nowEpochMillis: Long,
): Map<WorkKey, List<StateTransition>> {
    val updated = history.toMutableMap()
    items.forEach { item ->
        val previous = updated[item.key].orEmpty()
        if (previous.lastOrNull()?.state != item.state) {
            updated[item.key] = (previous + StateTransition(item.state, nowEpochMillis)).takeLast(MAX_TRANSITIONS_PER_WORK)
        }
    }
    val present = items.mapTo(mutableSetOf(), BackgroundWorkItem::key)
    val departed = updated.keys.filterNot(present::contains)
    departed.sortedBy { updated.getValue(it).last().atEpochMillis }
        .take((departed.size - MAX_DEPARTED_WORK).coerceAtLeast(0))
        .forEach(updated::remove)
    return updated
}
