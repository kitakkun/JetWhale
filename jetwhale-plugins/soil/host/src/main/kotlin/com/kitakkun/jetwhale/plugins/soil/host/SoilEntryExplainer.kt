package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryError
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import kotlin.time.Duration

/**
 * How long after a mutation's run ended other entries' events still count as what followed it.
 * Soil applies a mutation's effects right after the run, so they land well within this.
 */
private const val FOLLOW_UP_WINDOW_MILLIS = 2_000L

/**
 * The events a mutation's effects can set off in other entries. A subscription's values arrive on
 * their own schedule, so they are left out.
 */
private val FollowUpEventKinds = setOf(
    SoilEventKind.INVALIDATED,
    SoilEventKind.DATA_UPDATED,
    SoilEventKind.FETCH_STARTED,
    SoilEventKind.FETCH_SUCCEEDED,
    SoilEventKind.FETCH_FAILED,
    SoilEventKind.SUBSCRIPTION_RESTARTED,
)

/** One sentence or two about an entry, in the tone of what it reports. */
internal data class SoilEntryNote(val text: String, val tone: JwTone)

/**
 * What other entries did right after a mutation's run ended: invalidations, data updates, fetches
 * and subscription restarts. They followed [runEnd] in time; the app's code may not have caused all
 * of them.
 */
internal data class SoilMutationFollowUps(val runEnd: SoilEvent, val events: List<SoilEvent>)

/**
 * Puts into words what an entry's state means and what Soil does next, judged by the app's clock at
 * [agentNowEpochMillis] and from the [events] seen so far, oldest first.
 */
internal class SoilEntryExplainer(
    private val agentNowEpochMillis: Long,
    private val events: List<SoilEvent>,
    private val timeOfDayFormatter: TimeOfDayFormatter,
) {
    private val agentNowEpochSeconds = agentNowEpochMillis / 1000

    /** The notes on [listed], problems first. */
    fun notesOn(listed: ListedSoilEntry): List<SoilEntryNote> = buildList {
        val entry = listed.entry
        when (val state = entry.state) {
            is SoilEntryState.Query -> {
                addAll(queryProgressNotesOn(entry, state))
                freshnessNoteOn(entry, state)?.let(::add)
                if (state.isInvalidated) add(invalidationNoteOn(entry, state))
                entry.chunkParams?.let { chunkParams ->
                    val text = when (chunkParams.size) {
                        0 -> "No pages loaded yet."
                        1 -> "1 page loaded, fetched with param ${chunkParams.single()}."
                        else -> "${chunkParams.size} pages loaded, fetched with params ${chunkParams.joinToString()}."
                    }
                    add(SoilEntryNote(text, JwTone.Neutral))
                }
            }

            is SoilEntryState.Mutation -> addAll(mutationNotesOn(entry, state))

            is SoilEntryState.Subscription -> add(subscriptionNoteOn(state))
        }
        locationNoteOn(listed)?.let(::add)
    }

    /** The events of the entry [handle] names, newest first, at most [limit] of them. */
    fun recentEventsOf(handle: String, limit: Int): List<SoilEvent> = events.filter { it.handle == handle }.takeLast(limit).asReversed()

    /**
     * What followed the last run of the mutation [mutationHandle] names; null when that run's end is
     * not among the events.
     */
    fun followUpsOf(mutationHandle: String): SoilMutationFollowUps? {
        val runEnd = events.lastOrNull { it.handle == mutationHandle && (it.kind == SoilEventKind.MUTATION_SUCCEEDED || it.kind == SoilEventKind.MUTATION_FAILED) } ?: return null
        val followingEvents = events.filter {
            it.sequence > runEnd.sequence &&
                it.atEpochMillis - runEnd.atEpochMillis <= FOLLOW_UP_WINDOW_MILLIS &&
                it.handle != mutationHandle &&
                it.kind in FollowUpEventKinds
        }
        return SoilMutationFollowUps(runEnd = runEnd, events = followingEvents)
    }

    private fun queryProgressNotesOn(entry: SoilEntry, state: SoilEntryState.Query): List<SoilEntryNote> = buildList {
        if (state.status == SoilStatus.FAILURE) add(queryFailureNoteOn(entry, state))
        when (val fetchStatus = state.fetchStatus) {
            is SoilFetchStatus.Idle -> Unit

            is SoilFetchStatus.Fetching -> {
                val running = inFlightSpanOf(entry)?.let { "Fetching for $it" } ?: "Fetching"
                val meanwhile = if (fetchStatus.isValidating) " to refresh the data from ${describeRelativeToNow(state.replyUpdatedAt)}, which screens show meanwhile." else "."
                add(SoilEntryNote(running + meanwhile, JwTone.Accent))
            }

            is SoilFetchStatus.Paused -> add(
                if (state.isPausedAt(agentNowEpochSeconds)) {
                    SoilEntryNote(
                        "Paused after the error until ${timeOfDayFormatter.formatTimeOfDayToTheSecond(fetchStatus.unpauseAt)} (${describeRelativeToNow(fetchStatus.unpauseAt)}): Soil skips fetches until then, and afterwards fetches again only when a screen observes it, it is invalidated or resumed.",
                        JwTone.Warning,
                    )
                } else {
                    SoilEntryNote("The pause after the error ended ${describeRelativeToNow(fetchStatus.unpauseAt)}. Soil does not fetch again by itself: it does when a screen observes it, it is invalidated or resumed.", JwTone.Neutral)
                },
            )
        }
    }

    /**
     * How long the entry's fetch or run has gone on, e.g. `3s`; `at least 3s` when it was already
     * under way when the agent first saw the entry, which its start event then is not there to show.
     */
    private fun inFlightSpanOf(entry: SoilEntry): String? {
        val since = entry.inFlightSinceEpochMillis ?: return null
        val isStartRecorded = events.any { it.handle == entry.handle && it.atEpochMillis == since && (it.kind == SoilEventKind.FETCH_STARTED || it.kind == SoilEventKind.MUTATION_STARTED) }
        return (if (isStartRecorded) "" else "at least ") + describeSeconds((agentNowEpochMillis - since) / 1000)
    }

    private fun queryFailureNoteOn(entry: SoilEntry, state: SoilEntryState.Query): SoilEntryNote {
        val holding = if (state.hasReply) " It still holds the data from ${describeRelativeToNow(state.replyUpdatedAt)}." else " It has no data."
        val retries = entry.options["retryCount"]?.let { " Soil retries within a fetch, up to $it times, only for errors the query's shouldRetry accepts (by default those that are Retryable), so the inspector sees the outcome rather than each attempt." }.orEmpty()
        val failureCount = events.count { it.handle == entry.handle && it.kind == SoilEventKind.FETCH_FAILED }
        val repeated = if (failureCount > 1) " It failed $failureCount times in the timeline." else ""
        return SoilEntryNote("The last fetch failed ${describeRelativeToNow(state.errorUpdatedAt)}${state.error.toWithClause()}.$holding$retries$repeated", JwTone.Error)
    }

    private fun freshnessNoteOn(entry: SoilEntry, state: SoilEntryState.Query): SoilEntryNote? {
        if (!state.hasReply) return if (state.status == SoilStatus.PENDING) SoilEntryNote("No data yet.", JwTone.Neutral) else null
        val updated = if (state.replyUpdatedAt == 0L) "holds initial or preloaded data" else "updated ${describeRelativeToNow(state.replyUpdatedAt)}"
        val staleTime = entry.options["staleTime"]?.let { ", staleTime $it" }.orEmpty()
        return when {
            isNeverReached(state.staleAt, agentNowEpochSeconds) -> SoilEntryNote("Never goes stale$staleTime: Soil fetches it again only when it is invalidated.", JwTone.Neutral)

            !state.isStaleAt(agentNowEpochSeconds) -> SoilEntryNote("Fresh for another ${describeSeconds(state.staleAt - agentNowEpochSeconds)} ($updated$staleTime). Until then Soil answers from the cache without fetching.", JwTone.Success)

            else -> {
                val focus = entry.options["revalidateOnFocus"]?.let { "revalidateOnFocus $it" }
                val reconnect = entry.options["revalidateOnReconnect"]?.let { "revalidateOnReconnect $it" }
                val revalidation = listOfNotNull(focus, reconnect).joinToString().let { if (it.isEmpty()) "" else " ($it)" }
                SoilEntryNote(
                    "Stale ($updated$staleTime). Soil does not refetch on a timer: it refetches stale data when a screen starts observing it, when it is invalidated or resumed, and on focus or reconnect where the app reports them$revalidation.",
                    JwTone.Warning,
                )
            }
        }
    }

    /**
     * Soil refetches an observed query on invalidation even while it is paused after an error, but
     * holds back the fetch a newly observing screen asks for until the pause ends.
     */
    private fun invalidationNoteOn(entry: SoilEntry, state: SoilEntryState.Query): SoilEntryNote = SoilEntryNote(
        when {
            entry.isObserved -> "Invalidated: an observed query refetches right away."
            state.isPausedAt(agentNowEpochSeconds) -> "Invalidated: it refetches when a screen next observes it, once the pause after the error has ended."
            else -> "Invalidated: it refetches when a screen next observes it."
        },
        JwTone.Warning,
    )

    private fun mutationNotesOn(entry: SoilEntry, state: SoilEntryState.Mutation): List<SoilEntryNote> = buildList {
        when (state.status) {
            SoilStatus.IDLE -> add(SoilEntryNote(if (state.mutatedCount == 0) "Has not run yet." else "Reset after ${describeRunCount(state.mutatedCount)}.", JwTone.Neutral))

            SoilStatus.PENDING -> add(SoilEntryNote(inFlightSpanOf(entry)?.let { "Running for $it." } ?: "Running.", JwTone.Accent))

            SoilStatus.SUCCESS -> {
                val tookMillis = events.lastOrNull { it.handle == entry.handle && it.kind == SoilEventKind.MUTATION_SUCCEEDED }?.durationMillis
                add(SoilEntryNote("Ran ${describeRunCount(state.mutatedCount)}; the last run succeeded ${describeRelativeToNow(state.replyUpdatedAt)}${tookMillis?.let { " after ${describeDurationMillis(it)}" }.orEmpty()}.", JwTone.Success))
            }

            SoilStatus.FAILURE -> add(SoilEntryNote("The last run failed ${describeRelativeToNow(state.errorUpdatedAt)}${state.error.toWithClause()}.", JwTone.Error))
        }
        followUpsOf(entry.handle)?.let { add(followUpNoteOn(it.events)) }
    }

    private fun followUpNoteOn(followUpEvents: List<SoilEvent>): SoilEntryNote {
        if (followUpEvents.isEmpty()) return SoilEntryNote("No query was invalidated, updated or refetched within ${FOLLOW_UP_WINDOW_MILLIS / 1000}s after the last run.", JwTone.Neutral)
        val byEntry = followUpEvents.groupBy(SoilEvent::handle).values.joinToString("; ") { events -> "${events.first().entryId.label} (${events.map { it.kind.label.lowercase() }.distinct().joinToString()})" }
        return SoilEntryNote("Within ${FOLLOW_UP_WINDOW_MILLIS / 1000}s after the last run: $byEntry.", JwTone.Info)
    }

    private fun subscriptionNoteOn(state: SoilEntryState.Subscription): SoilEntryNote {
        val restarted = if (state.restartedAt == 0L) "" else " Restarted ${describeRelativeToNow(state.restartedAt)}."
        return when (state.status) {
            SoilStatus.FAILURE -> SoilEntryNote("Failed ${describeRelativeToNow(state.errorUpdatedAt)}${state.error.toWithClause()}.$restarted", JwTone.Error)
            SoilStatus.PENDING -> SoilEntryNote("Waiting for its first value.$restarted", JwTone.Neutral)
            SoilStatus.SUCCESS, SoilStatus.IDLE -> SoilEntryNote("Last received ${describeRelativeToNow(state.replyUpdatedAt)}.$restarted", JwTone.Neutral)
        }
    }

    private fun locationNoteOn(listed: ListedSoilEntry): SoilEntryNote? {
        val entry = listed.entry
        return when {
            listed.isGone -> SoilEntryNote("Soil dropped this mutation when nothing used it any more; this is the last state the app reported.", JwTone.Neutral)

            entry.location == SoilEntryLocation.INACTIVE -> SoilEntryNote(cacheLifetimeOf(entry), JwTone.Neutral)

            entry.location == SoilEntryLocation.ACTIVE_AND_CACHED -> SoilEntryNote("Active, with an older copy still in the cache.", JwTone.Neutral)

            !entry.isObserved -> SoilEntryNote(
                "No screen observes it. Soil keeps it active for keepAliveTime${entry.options["keepAliveTime"]?.let { " ($it)" }.orEmpty()} after the last screen lets go, then " +
                    if (entry.kind == SoilEntryKind.MUTATION) "drops it: Soil caches no mutations." else "moves it into the cache.",
                JwTone.Neutral,
            )

            else -> null
        }
    }

    private fun cacheLifetimeOf(entry: SoilEntry): String {
        val gcTime = entry.options["gcTime"]?.let(Duration::parseOrNull)
        val since = entry.inactiveSinceEpochMillis
        val unused = if (since == null) "Cached since before the inspector first read the cache" else "Cached and unused for ${describeSeconds((agentNowEpochMillis - since) / 1000)}"
        val dropped = when {
            gcTime == null -> if (since == null) ", so when Soil drops it is not known" else ""
            gcTime.isInfinite() -> "; Soil never drops it (gcTime $gcTime)"
            since == null -> "; Soil drops it gcTime ($gcTime) after it became inactive"
            else -> "; Soil drops it ${describeRelativeToNow((since + gcTime.inWholeMilliseconds) / 1000)} (gcTime $gcTime)"
        }
        return "$unused$dropped."
    }

    private fun describeRelativeToNow(epochSeconds: Long): String = describeEpochSeconds(epochSeconds, agentNowEpochSeconds, whenZero = "at an unknown time")
}

private fun describeRunCount(count: Int): String = if (count == 1) "once" else "$count times"

private fun SoilEntryError?.toWithClause(): String = when {
    this == null -> ""
    message == null -> " with $className"
    else -> " with $className: $message"
}
