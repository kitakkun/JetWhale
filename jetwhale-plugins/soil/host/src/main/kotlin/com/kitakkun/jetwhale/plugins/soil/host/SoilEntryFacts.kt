package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryId
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

/**
 * One label on an entry: its status, what it is doing, or where Soil holds it.
 *
 * @property isRoutine Says what most entries are, such as a success or a screen observing it; the
 *   list leaves these out so that the entries that differ stand out.
 */
internal data class SoilEntryBadge(val text: String, val tone: JwTone, val isRoutine: Boolean)

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

/** The id on one line: the namespace, then the tags in brackets when it has any. */
internal val SoilEntryId.label: String
    get() = if (tags.isEmpty()) namespace else namespace + tags.joinToString(prefix = " [", postfix = "]")

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

internal val SoilStatus.tone: JwTone
    get() = when (this) {
        SoilStatus.IDLE -> JwTone.Neutral
        SoilStatus.PENDING -> JwTone.Info
        SoilStatus.SUCCESS -> JwTone.Success
        SoilStatus.FAILURE -> JwTone.Error
    }

/** Whether a query's data is stale at [agentNowEpochSeconds], as Soil judges it: staleAt has passed. */
internal fun SoilEntryState.isStaleAt(agentNowEpochSeconds: Long): Boolean = this is SoilEntryState.Query && hasReply && staleAt < agentNowEpochSeconds

/**
 * Whether Soil holds a query's fetches back at [agentNowEpochSeconds], as it judges it: the query
 * stays marked paused after unpauseAt, but nothing is held back any more.
 */
internal fun SoilEntryState.isPausedAt(agentNowEpochSeconds: Long): Boolean {
    val fetchStatus = (this as? SoilEntryState.Query)?.fetchStatus
    return fetchStatus is SoilFetchStatus.Paused && fetchStatus.unpauseAt > agentNowEpochSeconds
}

/** What [listed] is labelled with, most telling first. */
internal fun badgesOf(listed: ListedSoilEntry, agentNowEpochSeconds: Long): List<SoilEntryBadge> = buildList {
    val entry = listed.entry
    val state = entry.state
    if (listed.isGone) add(SoilEntryBadge("Gone", JwTone.Neutral, isRoutine = false))
    add(SoilEntryBadge(state.status.label, state.status.tone, isRoutine = state.status == SoilStatus.SUCCESS))
    if (state is SoilEntryState.Query) {
        when (val fetchStatus = state.fetchStatus) {
            is SoilFetchStatus.Idle -> Unit
            is SoilFetchStatus.Fetching -> add(SoilEntryBadge(if (fetchStatus.isValidating) "Validating" else "Fetching", JwTone.Accent, isRoutine = false))
            is SoilFetchStatus.Paused -> if (state.isPausedAt(agentNowEpochSeconds)) add(SoilEntryBadge("Paused", JwTone.Warning, isRoutine = false))
        }
        if (state.isStaleAt(agentNowEpochSeconds)) add(SoilEntryBadge("Stale", JwTone.Warning, isRoutine = false))
        if (state.isInvalidated) add(SoilEntryBadge("Invalidated", JwTone.Warning, isRoutine = false))
    }
    when {
        entry.location == SoilEntryLocation.INACTIVE -> add(SoilEntryBadge("Inactive", JwTone.Neutral, isRoutine = false))
        entry.isObserved -> add(SoilEntryBadge("Observed", JwTone.Info, isRoutine = true))
        !listed.isGone -> add(SoilEntryBadge("Unobserved", JwTone.Neutral, isRoutine = false))
    }
}

/** Whether [epochSeconds] lies so far ahead that it stands for never, as an infinite staleTime does. */
internal fun isNeverReached(epochSeconds: Long, agentNowEpochSeconds: Long): Boolean = epochSeconds - agentNowEpochSeconds > NEVER_HORIZON_SECONDS

/**
 * [epochSeconds] relative to [agentNowEpochSeconds], e.g. `12s ago` or `in 3m`. Soil leaves a
 * timestamp at 0 until it sets it, so 0 reads as [whenZero]. The app's clock is estimated, and the
 * UI moves it on once a second, so a second either way reads as now.
 */
internal fun describeEpochSeconds(epochSeconds: Long, agentNowEpochSeconds: Long, whenZero: String): String {
    val delta = epochSeconds - agentNowEpochSeconds
    return when {
        epochSeconds == 0L -> whenZero
        isNeverReached(epochSeconds, agentNowEpochSeconds) -> "never"
        delta in -1L..1L -> "now"
        delta > 0 -> "in ${describeSeconds(delta)}"
        else -> "${describeSeconds(-delta)} ago"
    }
}

/** A span of time to the second, e.g. `45s`, `3m 20s` or `2h 5m`. */
internal fun describeSeconds(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 60 * 60 -> "${seconds / 60}m ${seconds % 60}s"
    seconds < 24 * 60 * 60 -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
    else -> "${seconds / 86_400}d ${seconds % 86_400 / 3600}h"
}

/** How long a fetch or mutation took, e.g. `340ms` or `2.4s`. */
internal fun describeDurationMillis(millis: Long): String = when {
    millis < 1_000 -> "${millis}ms"
    millis < 60_000 -> "${millis / 1000}.${millis % 1000 / 100}s"
    else -> describeSeconds(millis / 1000)
}
