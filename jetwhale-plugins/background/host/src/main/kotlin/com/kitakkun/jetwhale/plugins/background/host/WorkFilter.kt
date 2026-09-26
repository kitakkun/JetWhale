package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState

/**
 * What the list shows.
 *
 * @property states The states to show; empty shows every state.
 * @property query Matched, ignoring case, against the work's name, id, tags and source.
 */
internal data class WorkFilter(val states: Set<WorkState>, val query: String) {
    fun matches(item: BackgroundWorkItem): Boolean {
        if (states.isNotEmpty() && item.state !in states) return false
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return (listOf(item.name, item.id, item.source) + item.tags).any { it.contains(needle, ignoreCase = true) }
    }
}

/** Work that has not finished: what "Pending" in the filter means. */
internal val UNFINISHED_STATES: Set<WorkState> = setOf(WorkState.Enqueued, WorkState.Blocked, WorkState.Scheduled, WorkState.Running)

/** Work that has finished. */
internal val FINISHED_STATES: Set<WorkState> = setOf(WorkState.Succeeded, WorkState.Failed, WorkState.Cancelled)

/** A class name as the list shows it: without the package, which is the same on every row. */
internal fun shortName(name: String): String = name.substringAfterLast('.')
