package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryError
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryId
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import soil.query.MutationModel
import soil.query.MutationStatus
import soil.query.QueryChunk
import soil.query.QueryFetchStatus
import soil.query.QueryModel
import soil.query.QueryStatus
import soil.query.SubscriptionModel
import soil.query.SubscriptionStatus
import soil.query.core.DataModel
import soil.query.core.UniqueId
import soil.query.core.getOrNull
import soil.query.core.isNone
import kotlin.time.Clock

/**
 * What changed between two readings of the cache.
 *
 * @property revision The tracker's revision after the reading, bumped whenever anything changed.
 * @property events What the changes amount to, oldest first.
 */
internal data class SoilCacheChanges(
    val upserts: List<SoilEntry>,
    val removedHandles: List<String>,
    val revision: Long,
    val events: List<SoilEvent>,
) {
    val isEmpty: Boolean get() = upserts.isEmpty() && removedHandles.isEmpty()
}

/**
 * The entries as last reported to the host, and what a new reading changes about them. Soil
 * replaces a state object on every change, so a model that is the same instance as last time is
 * not converted again. Not thread-safe.
 */
internal class SoilCacheTracker(
    private val handles: SoilEntryHandles,
    private val clock: Clock,
) {
    private val eventLog = SoilEventLog()
    private var trackedEntries: Map<SoilEntryKey, TrackedEntry> = emptyMap()

    var revision: Long = 0
        private set

    val entries: List<SoilEntry> get() = trackedEntries.values.map(TrackedEntry::entry)

    /** The latest events, oldest first, as far back as the log keeps them. */
    val recentEvents: List<SoilEvent> get() = eventLog.recentEvents

    fun entryOf(key: SoilEntryKey): SoilEntry? = trackedEntries[key]?.entry

    /** Takes [records] as the cache's current contents and returns how they differ from the last ones. */
    fun replaceEntriesWith(records: List<SoilCacheRecord>): SoilCacheChanges {
        val nowEpochMillis = clock.now().toEpochMilliseconds()
        val updatedTrackedEntries = LinkedHashMap<SoilEntryKey, TrackedEntry>()
        val changedEntries = mutableListOf<Pair<SoilEntry?, SoilEntry>>()
        records.forEach { record ->
            val previous = trackedEntries[record.key]
            val isSameReading = previous != null &&
                previous.model === record.model &&
                previous.entry.location == record.location &&
                previous.entry.isObserved == record.isObserved &&
                previous.recordOptions == record.options
            val replyRevision = when {
                previous == null -> 0L
                previous.model.reply !== record.model.reply -> previous.entry.replyRevision + 1
                else -> previous.entry.replyRevision
            }
            val entry = if (isSameReading) {
                previous.entry
            } else {
                record.toSoilEntry(previous?.entry, handles.handleOf(record.key), replyRevision, nowEpochMillis)
            }
            if (entry != previous?.entry) changedEntries += previous?.entry to entry
            updatedTrackedEntries[record.key] = TrackedEntry(model = record.model, recordOptions = record.options, entry = entry)
        }
        val removedKeys = trackedEntries.keys - updatedTrackedEntries.keys
        val removedEntries = removedKeys.mapNotNull { trackedEntries[it]?.entry }
        removedKeys.forEach(handles::retire)
        trackedEntries = updatedTrackedEntries
        val upserts = changedEntries.map { (_, entry) -> entry }
        if (upserts.isNotEmpty() || removedEntries.isNotEmpty()) revision++
        // Mutations first: Soil applies a mutation's effects, such as invalidations, when its run
        // ends, so in a reading that catches both, the run's end comes before what it set off. The
        // host finds what followed a run by sequence.
        val events = changedEntries.sortedBy { (_, entry) -> entry.kind != SoilEntryKind.MUTATION }.flatMap { (previous, entry) -> eventLog.recordChange(previous, entry, nowEpochMillis) } +
            removedEntries.flatMap { eventLog.recordChange(it, null, nowEpochMillis) }
        return SoilCacheChanges(upserts = upserts, removedHandles = removedEntries.map(SoilEntry::handle), revision = revision, events = events)
    }

    /**
     * @property recordOptions The options as read, which an inactive entry no longer has: its
     *   [entry] carries the ones it had while active.
     */
    private class TrackedEntry(val model: DataModel<*>, val recordOptions: Map<String, String>, val entry: SoilEntry)
}

/**
 * The entry this record describes, carrying over from [previous] what Soil does not keep: the
 * options of an entry that moved into the cache, when it moved, and since when it has been seen
 * fetching or running, all as of [nowEpochMillis]. The stores are read only every so often, so an entry
 * seen for the first time may have been cached or in flight for a while already: when it moved is
 * not known, and its fetch or run is at least as old as this reading.
 */
private fun SoilCacheRecord.toSoilEntry(previous: SoilEntry?, handle: String, replyRevision: Long, nowEpochMillis: Long): SoilEntry {
    val isInactive = location == SoilEntryLocation.INACTIVE
    val state = model.toSoilEntryState()
    return SoilEntry(
        handle = handle,
        kind = key.kind,
        location = location,
        id = key.id.toSoilEntryId(),
        state = state,
        isObserved = isObserved,
        options = if (isInactive && options.isEmpty()) previous?.options.orEmpty() else options,
        replyRevision = replyRevision,
        inactiveSinceEpochMillis = when {
            !isInactive || previous == null -> null
            previous.location == SoilEntryLocation.INACTIVE -> previous.inactiveSinceEpochMillis
            else -> nowEpochMillis
        },
        inFlightSinceEpochMillis = when {
            !state.isInFlight -> null
            previous != null && previous.state.isInFlight -> previous.inFlightSinceEpochMillis
            else -> nowEpochMillis
        },
        chunkParams = if (key.kind == SoilEntryKind.INFINITE_QUERY) (model.reply.getOrNull() as? List<*>)?.map { (it as? QueryChunk<*, *>)?.param.toString() } else null,
    )
}

private fun UniqueId.toSoilEntryId(): SoilEntryId = SoilEntryId(
    className = this::class.simpleName ?: "UniqueId",
    namespace = namespace,
    tags = tags.map(Any::toString),
)

private fun DataModel<*>.toSoilEntryState(): SoilEntryState {
    val entryError = error?.let { SoilEntryError(className = it::class.simpleName ?: "Throwable", message = it.message) }
    return when (this) {
        is QueryModel<*> -> SoilEntryState.Query(
            status = when (status) {
                QueryStatus.Pending -> SoilStatus.PENDING
                QueryStatus.Success -> SoilStatus.SUCCESS
                QueryStatus.Failure -> SoilStatus.FAILURE
            },
            hasReply = !reply.isNone,
            replyUpdatedAt = replyUpdatedAt,
            error = entryError,
            errorUpdatedAt = errorUpdatedAt,
            staleAt = staleAt,
            fetchStatus = when (val fetchStatus = fetchStatus) {
                is QueryFetchStatus.Idle -> SoilFetchStatus.Idle
                is QueryFetchStatus.Fetching -> SoilFetchStatus.Fetching(isValidating = fetchStatus.isValidating)
                is QueryFetchStatus.Paused -> SoilFetchStatus.Paused(unpauseAt = fetchStatus.unpauseAt)
            },
            isInvalidated = isInvalidated,
        )

        is MutationModel<*> -> SoilEntryState.Mutation(
            status = when (status) {
                MutationStatus.Idle -> SoilStatus.IDLE
                MutationStatus.Pending -> SoilStatus.PENDING
                MutationStatus.Success -> SoilStatus.SUCCESS
                MutationStatus.Failure -> SoilStatus.FAILURE
            },
            hasReply = !reply.isNone,
            replyUpdatedAt = replyUpdatedAt,
            error = entryError,
            errorUpdatedAt = errorUpdatedAt,
            mutatedCount = mutatedCount,
            submittedAt = submittedAt,
        )

        is SubscriptionModel<*> -> SoilEntryState.Subscription(
            status = when (status) {
                SubscriptionStatus.Pending -> SoilStatus.PENDING
                SubscriptionStatus.Success -> SoilStatus.SUCCESS
                SubscriptionStatus.Failure -> SoilStatus.FAILURE
            },
            hasReply = !reply.isNone,
            replyUpdatedAt = replyUpdatedAt,
            error = entryError,
            errorUpdatedAt = errorUpdatedAt,
            restartedAt = restartedAt,
        )

        else -> error("Soil models are queries, mutations or subscriptions, not ${this::class.simpleName}")
    }
}
