package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryError
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus

/** How many events the agent keeps for a host that connects later. */
private const val RECENT_EVENT_LIMIT = 500

/**
 * Turns the difference between two readings of an entry into events, and keeps the latest of them.
 * A fetch or mutation that ends gets its duration when the log recorded its start. Not thread-safe.
 */
internal class SoilEventLog {
    private val keptEvents = ArrayDeque<SoilEvent>()
    private var nextSequence = 1L

    /** The entries whose current fetch or run started while the log watched, so its duration is known. */
    private val handlesWithRecordedStart = mutableSetOf<String>()

    val recentEvents: List<SoilEvent> get() = keptEvents.toList()

    /**
     * Records and returns the events that turn [previous] into [current], seen at [nowEpochMillis]. A
     * null [previous] is an entry seen for the first time, a null [current] one Soil dropped.
     */
    fun recordChange(previous: SoilEntry?, current: SoilEntry?, nowEpochMillis: Long): List<SoilEvent> {
        val entry = current ?: previous ?: return emptyList()
        val changes = changesBetween(previous, current, nowEpochMillis)
        when {
            changes.any { it.kind == SoilEventKind.FETCH_STARTED || it.kind == SoilEventKind.MUTATION_STARTED } -> handlesWithRecordedStart += entry.handle
            current?.state?.isInFlight != true -> handlesWithRecordedStart -= entry.handle
        }
        val events = changes.map { change ->
            SoilEvent(
                sequence = nextSequence++,
                atEpochMillis = nowEpochMillis,
                handle = entry.handle,
                entryKind = entry.kind,
                entryId = entry.id,
                kind = change.kind,
                durationMillis = change.durationMillis,
                detail = change.detail,
            )
        }
        keptEvents.addAll(events)
        while (keptEvents.size > RECENT_EVENT_LIMIT) keptEvents.removeFirst()
        return events
    }

    private fun changesBetween(previous: SoilEntry?, current: SoilEntry?, nowEpochMillis: Long): List<EntryChange> = buildList {
        if (current == null) {
            add(EntryChange(SoilEventKind.REMOVED))
            return@buildList
        }
        if (previous == null) {
            add(EntryChange(SoilEventKind.APPEARED, detail = appearanceDetailOf(current)))
            return@buildList
        }
        val wasInactive = previous.location == SoilEntryLocation.INACTIVE
        val isInactive = current.location == SoilEntryLocation.INACTIVE
        if (!wasInactive && isInactive) add(EntryChange(SoilEventKind.BECAME_INACTIVE))
        if (wasInactive && !isInactive) add(EntryChange(SoilEventKind.BECAME_ACTIVE))
        if (!previous.isObserved && current.isObserved) add(EntryChange(SoilEventKind.OBSERVED))
        if (previous.isObserved && !current.isObserved) add(EntryChange(SoilEventKind.UNOBSERVED))
        val isReplyReplaced = previous.replyRevision != current.replyRevision
        val durationMillis = previous.inFlightSinceEpochMillis?.takeIf { previous.handle in handlesWithRecordedStart }?.let { nowEpochMillis - it }
        when (val state = current.state) {
            is SoilEntryState.Query -> addAll(queryChanges(previous.state as? SoilEntryState.Query, state, isReplyReplaced, durationMillis, nowEpochMillis))
            is SoilEntryState.Mutation -> addAll(mutationChanges(previous.state as? SoilEntryState.Mutation, state, isReplyReplaced, durationMillis))
            is SoilEntryState.Subscription -> addAll(subscriptionChanges(previous.state as? SoilEntryState.Subscription, state, isReplyReplaced))
        }
    }

    /**
     * What an entry seen for the first time was already doing. When that started is not known, so
     * a fetch or run under way gets no start event and no duration when it ends.
     */
    private fun appearanceDetailOf(entry: SoilEntry): String? {
        val state = entry.state
        return when {
            entry.location == SoilEntryLocation.INACTIVE -> "cached"
            state is SoilEntryState.Query && state.fetchStatus is SoilFetchStatus.Fetching -> "while fetching"
            state is SoilEntryState.Query && state.fetchStatus is SoilFetchStatus.Paused -> "paused after an error"
            state is SoilEntryState.Mutation && state.status == SoilStatus.PENDING -> "while running"
            else -> null
        }
    }

    private fun queryChanges(previous: SoilEntryState.Query?, current: SoilEntryState.Query, isReplyReplaced: Boolean, durationMillis: Long?, nowEpochMillis: Long): List<EntryChange> = buildList {
        if (previous != null && !previous.isInvalidated && current.isInvalidated) add(EntryChange(SoilEventKind.INVALIDATED))
        fetchEndOf(previous, current, isReplyReplaced, durationMillis)?.let(::add)
        val fetchStatus = current.fetchStatus
        if (previous?.fetchStatus !is SoilFetchStatus.Fetching && fetchStatus is SoilFetchStatus.Fetching) {
            add(EntryChange(SoilEventKind.FETCH_STARTED, detail = if (fetchStatus.isValidating) "revalidating its data" else null))
        }
        if (fetchStatus is SoilFetchStatus.Paused && previous?.fetchStatus !is SoilFetchStatus.Paused) {
            add(EntryChange(SoilEventKind.FETCH_PAUSED, detail = "for ${(fetchStatus.unpauseAt - nowEpochMillis / 1000).coerceAtLeast(0)}s"))
        }
    }

    /**
     * How the fetch [previous] was running ended, or, when none was seen running, what one that
     * came and went between the two readings left behind. Soil times errors to the second, so a
     * fetch seen ending in a failure is one even when errorUpdatedAt did not move.
     */
    private fun fetchEndOf(previous: SoilEntryState.Query?, current: SoilEntryState.Query, isReplyReplaced: Boolean, durationMillis: Long?): EntryChange? {
        if (current.fetchStatus is SoilFetchStatus.Fetching) return null
        val wasFetching = previous?.fetchStatus is SoilFetchStatus.Fetching
        val isNewFetchFailure = current.error != null &&
            ((previous != null && current.errorUpdatedAt != previous.errorUpdatedAt) || (wasFetching && current.status == SoilStatus.FAILURE))
        return when {
            isNewFetchFailure -> EntryChange(SoilEventKind.FETCH_FAILED, durationMillis, current.error?.describe())
            !wasFetching -> if (isReplyReplaced) EntryChange(SoilEventKind.DATA_UPDATED) else null
            isReplyReplaced -> EntryChange(SoilEventKind.FETCH_SUCCEEDED, durationMillis)
            else -> EntryChange(SoilEventKind.FETCH_SUCCEEDED, durationMillis, "the data did not change")
        }
    }

    private fun mutationChanges(previous: SoilEntryState.Mutation?, current: SoilEntryState.Mutation, isReplyReplaced: Boolean, durationMillis: Long?): List<EntryChange> = buildList {
        mutationEndOf(previous, current, isReplyReplaced, durationMillis)?.let(::add)
        if (previous?.status != SoilStatus.PENDING && current.status == SoilStatus.PENDING) add(EntryChange(SoilEventKind.MUTATION_STARTED))
    }

    /** How the run [previous] was in ended, or how one that came and went between the two readings did. */
    private fun mutationEndOf(previous: SoilEntryState.Mutation?, current: SoilEntryState.Mutation, isReplyReplaced: Boolean, durationMillis: Long?): EntryChange? {
        if (previous == null || current.status == SoilStatus.PENDING) return null
        val isNewError = current.errorUpdatedAt != previous.errorUpdatedAt && current.error != null
        val isNewRun = current.mutatedCount != previous.mutatedCount || isReplyReplaced
        if (previous.status != SoilStatus.PENDING && !isNewError && !isNewRun) return null
        return when (current.status) {
            SoilStatus.FAILURE -> EntryChange(SoilEventKind.MUTATION_FAILED, durationMillis, current.error?.describe())
            SoilStatus.SUCCESS -> EntryChange(SoilEventKind.MUTATION_SUCCEEDED, durationMillis)
            SoilStatus.IDLE, SoilStatus.PENDING -> null
        }
    }

    private fun subscriptionChanges(previous: SoilEntryState.Subscription?, current: SoilEntryState.Subscription, isReplyReplaced: Boolean): List<EntryChange> = buildList {
        if (previous == null) return@buildList
        if (current.restartedAt != previous.restartedAt) add(EntryChange(SoilEventKind.SUBSCRIPTION_RESTARTED))
        if (isReplyReplaced) add(EntryChange(SoilEventKind.SUBSCRIPTION_DATA_RECEIVED))
        val isNewFailure = current.error != null &&
            (current.errorUpdatedAt != previous.errorUpdatedAt || (previous.status != SoilStatus.FAILURE && current.status == SoilStatus.FAILURE))
        if (isNewFailure) add(EntryChange(SoilEventKind.SUBSCRIPTION_FAILED, detail = current.error?.describe()))
    }

    private class EntryChange(val kind: SoilEventKind, val durationMillis: Long? = null, val detail: String? = null)
}

private fun SoilEntryError.describe(): String = if (message == null) className else "$className: $message"
