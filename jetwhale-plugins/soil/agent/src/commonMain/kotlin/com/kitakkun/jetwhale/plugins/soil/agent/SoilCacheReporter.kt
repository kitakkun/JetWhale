package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheSnapshot
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntriesChanged
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import soil.query.core.epoch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Soil says nothing when an entry is added or dropped, so the stores are read again this often.
 * Changes to an active entry's state wake the reader sooner.
 */
private val STORE_POLL_INTERVAL: Duration = 500.milliseconds

/** How long a change waits for others before the cache is read, so a burst arrives as one event. */
private val CHANGE_COALESCING_DELAY: Duration = 100.milliseconds

/**
 * Reports the app's Soil cache to the host: whole on request, then as changes. Readings and the
 * tracker's bookkeeping are serialized by one lock, so every event's revision is greater than that
 * of the snapshot or event before it.
 */
internal class SoilCacheReporter(
    private val cache: InspectedSoilCache,
    private val handles: SoilEntryHandles,
) {
    private val tracker = SoilCacheTracker(handles)
    private val trackerLock = Mutex()
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)

    suspend fun takeSnapshot(): SoilCacheSnapshot = trackerLock.withLock {
        tracker.update(cache.readRecords())
        SoilCacheSnapshot(coverage = cache.coverage, entries = tracker.entries, revision = tracker.revision, agentEpochSeconds = epoch())
    }

    suspend fun keyOf(handle: String): SoilEntryKey? = trackerLock.withLock { handles.keyOf(handle) }

    /** The entry as last read, which is what the host has seen or is about to. */
    suspend fun entryOf(key: SoilEntryKey): SoilEntry? = trackerLock.withLock { tracker.entryOf(key) }

    /** Reads the cache again soon, rather than at the next poll. */
    fun requestReading() {
        wakeUps.trySend(Unit)
    }

    /**
     * Reads the cache until cancelled, handing [send] whatever changed since the last reading. Runs
     * the state watchers of active entries in the caller's scope.
     */
    suspend fun reportChanges(send: (SoilEntriesChanged) -> Unit) = coroutineScope {
        val stateWatchers = mutableMapOf<SoilEntryKey, Job>()
        while (true) {
            val records = trackerLock.withLock {
                val records = cache.readRecords()
                val changes = tracker.update(records)
                if (!changes.isEmpty) {
                    send(SoilEntriesChanged(upserts = changes.upserts, removedHandles = changes.removedHandles, revision = changes.revision, agentEpochSeconds = epoch()))
                }
                records
            }
            val watchedFlows = records.mapNotNull { record -> record.stateFlow?.let { record.key to it } }.toMap()
            (stateWatchers.keys - watchedFlows.keys).forEach { key -> stateWatchers.remove(key)?.cancel() }
            watchedFlows.forEach { (key, stateFlow) ->
                if (key !in stateWatchers) stateWatchers[key] = launch { stateFlow.drop(1).collect { wakeUps.trySend(Unit) } }
            }
            withTimeoutOrNull(STORE_POLL_INTERVAL) { wakeUps.receive() }
            delay(CHANGE_COALESCING_DELAY)
        }
    }
}
