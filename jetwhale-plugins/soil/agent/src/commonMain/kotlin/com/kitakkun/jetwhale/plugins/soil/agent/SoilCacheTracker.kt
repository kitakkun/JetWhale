package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryError
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryId
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import soil.query.MutationModel
import soil.query.MutationStatus
import soil.query.QueryFetchStatus
import soil.query.QueryModel
import soil.query.QueryStatus
import soil.query.SubscriptionModel
import soil.query.SubscriptionStatus
import soil.query.core.DataModel
import soil.query.core.UniqueId
import soil.query.core.isNone

/**
 * What changed between two readings of the cache.
 *
 * @property revision The tracker's revision after the reading, bumped whenever anything changed.
 */
internal data class SoilCacheChanges(
    val upserts: List<SoilEntry>,
    val removedHandles: List<String>,
    val revision: Long,
) {
    val isEmpty: Boolean get() = upserts.isEmpty() && removedHandles.isEmpty()
}

/**
 * The entries as last reported to the host, and what a new reading changes about them. Soil
 * replaces a state object on every change, so a model that is the same instance as last time is
 * not converted again. Not thread-safe.
 */
internal class SoilCacheTracker(private val handles: SoilEntryHandles) {
    private var trackedEntries: Map<SoilEntryKey, TrackedEntry> = emptyMap()

    var revision: Long = 0
        private set

    val entries: List<SoilEntry> get() = trackedEntries.values.map(TrackedEntry::entry)

    fun entryOf(key: SoilEntryKey): SoilEntry? = trackedEntries[key]?.entry

    /** Takes [records] as the cache's current contents and returns how they differ from the last ones. */
    fun replaceEntriesWith(records: List<SoilCacheRecord>): SoilCacheChanges {
        val updatedTrackedEntries = LinkedHashMap<SoilEntryKey, TrackedEntry>()
        val upserts = mutableListOf<SoilEntry>()
        records.forEach { record ->
            val previous = trackedEntries[record.key]
            val isSameReading = previous != null &&
                previous.model === record.model &&
                previous.entry.location == record.location &&
                previous.entry.isObserved == record.isObserved &&
                previous.entry.options == record.options
            val entry = if (isSameReading) previous.entry else record.toSoilEntry(handles.handleOf(record.key))
            if (entry != previous?.entry) upserts += entry
            updatedTrackedEntries[record.key] = TrackedEntry(model = record.model, entry = entry)
        }
        val removedKeys = trackedEntries.keys - updatedTrackedEntries.keys
        val removedHandles = removedKeys.mapNotNull { key -> trackedEntries[key]?.entry?.handle }
        removedKeys.forEach(handles::retire)
        trackedEntries = updatedTrackedEntries
        if (upserts.isNotEmpty() || removedHandles.isNotEmpty()) revision++
        return SoilCacheChanges(upserts = upserts, removedHandles = removedHandles, revision = revision)
    }

    private class TrackedEntry(val model: DataModel<*>, val entry: SoilEntry)
}

private fun SoilCacheRecord.toSoilEntry(handle: String): SoilEntry = SoilEntry(
    handle = handle,
    kind = key.kind,
    location = location,
    id = key.id.toSoilEntryId(),
    state = model.toSoilEntryState(),
    isObserved = isObserved,
    options = options,
)

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
