package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheSnapshot
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntriesChanged
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Soil says nothing when an entry is added or dropped, so the stores are read again this often.
 * Changes to an active entry's state wake the reader sooner.
 */
private val STORE_POLL_INTERVAL: Duration = 500.milliseconds

/** How long a change waits for others before the cache is read, so a burst arrives as one event. */
private val CHANGE_COALESCING_DELAY: Duration = 50.milliseconds

/**
 * Reports the app's Soil cache to the host: whole on request, then as changes. Readings and the
 * tracker's bookkeeping are serialized by one lock, so every event's revision is greater than that
 * of the snapshot or event before it.
 */
internal class SoilCacheReporter(
    private val cache: InspectedSoilCache,
    private val handles: SoilEntryHandles,
    private val clock: Clock,
) {
    private val tracker = SoilCacheTracker(handles, clock)
    private val trackerLock = Mutex()
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)

    suspend fun takeSnapshot(): SoilCacheSnapshot = trackerLock.withLock {
        tracker.replaceEntriesWith(cache.readRecords())
        SoilCacheSnapshot(coverage = cache.coverage, entries = tracker.entries, revision = tracker.revision, agentEpochMillis = clock.now().toEpochMilliseconds(), recentEvents = tracker.recentEvents)
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
        val stateWatchers = StateWatchers(scope = this, wakeUps = wakeUps)
        while (true) {
            val records = trackerLock.withLock {
                val records = cache.readRecords()
                val changes = tracker.replaceEntriesWith(records)
                if (!changes.isEmpty) {
                    send(SoilEntriesChanged(upserts = changes.upserts, removedHandles = changes.removedHandles, revision = changes.revision, agentEpochMillis = clock.now().toEpochMilliseconds(), events = changes.events))
                }
                records
            }
            stateWatchers.watchOnlyStateFlowsOf(records)
            val isWokenByChange = withTimeoutOrNull(STORE_POLL_INTERVAL) { wakeUps.receive() } != null
            if (isWokenByChange) delay(CHANGE_COALESCING_DELAY)
        }
    }
}

/** The jobs in [scope] that wake the reader through [wakeUps] when an active entry's state changes. */
private class StateWatchers(private val scope: CoroutineScope, private val wakeUps: Channel<Unit>) {
    private val watchersByKey = mutableMapOf<SoilEntryKey, StateWatcher>()

    /**
     * Watches the state flow of each active entry in [records] and lets go of the rest, including a
     * flow Soil replaced when it dropped an entry and created it again between two readings.
     */
    fun watchOnlyStateFlowsOf(records: List<SoilCacheRecord>) {
        val activeStateFlowsByKey = records.mapNotNull { record -> record.stateFlow?.let { record.key to it } }.toMap()
        val staleKeys = watchersByKey.filter { (key, watcher) -> activeStateFlowsByKey[key] !== watcher.stateFlow }.keys
        staleKeys.forEach { key -> watchersByKey.remove(key)?.job?.cancel() }
        activeStateFlowsByKey.forEach { (key, stateFlow) ->
            if (key !in watchersByKey) watchersByKey[key] = StateWatcher(stateFlow, scope.launch { stateFlow.drop(1).collect { wakeUps.trySend(Unit) } })
        }
    }

    private class StateWatcher(val stateFlow: StateFlow<*>, val job: Job)
}
