package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus

/**
 * Soil stores an infinite staleTime as `Long.MAX_VALUE`; a staleAt further ahead than this is read
 * as never.
 */
private const val NEVER_HORIZON_SECONDS = 100L * 365 * 24 * 60 * 60

/** One label on an entry: its status, what it is doing, or where Soil holds it. */
internal data class SoilEntryBadge(val text: String, val tone: JwTone)

internal val SoilEntryAction.label: String
    get() = when (this) {
        SoilEntryAction.INVALIDATE -> "Invalidate"
        SoilEntryAction.RESUME -> "Resume"
        SoilEntryAction.REMOVE_INACTIVE -> "Remove"
    }

internal val SoilEntryKind.label: String
    get() = when (this) {
        SoilEntryKind.QUERY -> "Query"
        SoilEntryKind.INFINITE_QUERY -> "Infinite query"
        SoilEntryKind.MUTATION -> "Mutation"
        SoilEntryKind.SUBSCRIPTION -> "Subscription"
    }

internal val SoilEntryKind.pluralLabel: String
    get() = when (this) {
        SoilEntryKind.QUERY -> "Queries"
        SoilEntryKind.INFINITE_QUERY -> "Infinite queries"
        SoilEntryKind.MUTATION -> "Mutations"
        SoilEntryKind.SUBSCRIPTION -> "Subscriptions"
    }

internal val SoilEntryLocation.label: String
    get() = when (this) {
        SoilEntryLocation.ACTIVE -> "Active"
        SoilEntryLocation.INACTIVE -> "Inactive"
        SoilEntryLocation.ACTIVE_AND_CACHED -> "Active, with an older copy cached"
    }

internal val SoilStatus.label: String
    get() = when (this) {
        SoilStatus.IDLE -> "Idle"
        SoilStatus.PENDING -> "Pending"
        SoilStatus.SUCCESS -> "Success"
        SoilStatus.FAILURE -> "Failure"
    }

/** Whether a query's data is stale at [agentNowEpochSeconds], as Soil judges it: staleAt has passed. */
internal fun SoilEntryState.isStaleAt(agentNowEpochSeconds: Long): Boolean = this is SoilEntryState.Query && hasReply && staleAt < agentNowEpochSeconds

/** What the list and the detail pane label [listed] with, most telling first. */
internal fun badgesOf(listed: ListedSoilEntry, agentNowEpochSeconds: Long): List<SoilEntryBadge> = buildList {
    val entry = listed.entry
    val state = entry.state
    if (listed.isGone) add(SoilEntryBadge("Gone", JwTone.Neutral))
    add(
        when (state.status) {
            SoilStatus.IDLE -> SoilEntryBadge("Idle", JwTone.Neutral)
            SoilStatus.PENDING -> SoilEntryBadge("Pending", JwTone.Info)
            SoilStatus.SUCCESS -> SoilEntryBadge("Success", JwTone.Success)
            SoilStatus.FAILURE -> SoilEntryBadge("Failure", JwTone.Error)
        },
    )
    if (state is SoilEntryState.Query) {
        when (val fetchStatus = state.fetchStatus) {
            is SoilFetchStatus.Idle -> Unit
            is SoilFetchStatus.Fetching -> add(SoilEntryBadge(if (fetchStatus.isValidating) "Validating" else "Fetching", JwTone.Accent))
            is SoilFetchStatus.Paused -> add(SoilEntryBadge("Paused", JwTone.Warning))
        }
        if (state.isStaleAt(agentNowEpochSeconds)) add(SoilEntryBadge("Stale", JwTone.Warning))
        if (state.isInvalidated) add(SoilEntryBadge("Invalidated", JwTone.Warning))
    }
    if (entry.location == SoilEntryLocation.INACTIVE) add(SoilEntryBadge("Inactive", JwTone.Neutral))
    if (entry.isObserved) add(SoilEntryBadge("Observed", JwTone.Info))
}

/**
 * [epochSeconds] relative to [agentNowEpochSeconds], e.g. `12s ago` or `in 3m`. Soil leaves a
 * timestamp at 0 until it sets it, so 0 reads as [whenZero].
 */
internal fun describeEpochSeconds(epochSeconds: Long, agentNowEpochSeconds: Long, whenZero: String): String {
    val delta = epochSeconds - agentNowEpochSeconds
    return when {
        epochSeconds == 0L -> whenZero
        delta > NEVER_HORIZON_SECONDS -> "never"
        delta == 0L -> "now"
        delta > 0 -> "in ${describeSeconds(delta)}"
        else -> "${describeSeconds(-delta)} ago"
    }
}

private fun describeSeconds(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 60 * 60 -> "${seconds / 60}m ${seconds % 60}s"
    seconds < 24 * 60 * 60 -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
    else -> "${seconds / 86_400}d ${seconds % 86_400 / 3600}h"
}
