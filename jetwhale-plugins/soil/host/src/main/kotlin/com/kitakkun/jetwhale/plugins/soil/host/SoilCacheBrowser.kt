package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheCoverage
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheSnapshot
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntriesChanged
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionResult
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** How many mutations Soil has dropped stay listed; the oldest goes first. */
private const val GONE_MUTATION_LIMIT = 50

/** How many events the timeline keeps; the oldest goes first. */
private const val EVENT_LIMIT = 1_000

internal data class SoilBrowserStatus(val message: String, val isError: Boolean)

/**
 * An entry as the host lists it.
 *
 * @property isGone Marks a mutation Soil no longer holds. A mutation lives only as long as the
 *   screen that runs it, so its last state is kept to stay visible after a short run.
 */
internal data class ListedSoilEntry(val entry: SoilEntry, val isGone: Boolean)

/** The selected entry's value, as far as it has been read from the app. */
internal sealed interface SoilValueLoad {
    data object Loading : SoilValueLoad

    data class Loaded(val value: SoilEntryValue) : SoilValueLoad

    data class Failed(val message: String) : SoilValueLoad
}

/** What the inspector's UI does with the cache. */
internal interface SoilInspectorActions {
    fun refresh()

    /** Shows the entry [handle] names in the detail pane and reads its value. */
    fun select(handle: String)

    fun reloadSelectedValue()

    fun runActionOnSelected(action: SoilEntryAction)

    /** Marks the event [sequence] numbers as the one looked at, and selects its entry if Soil still holds it. */
    fun selectEvent(sequence: Long)

    /** Empties the timeline; events that arrive later still show. */
    fun clearEvents()

    fun dismissStatus()
}

/**
 * The app's Soil cache as the host has been told about it: a snapshot taken on connect, kept
 * current by the agent's change events. Every call to the app goes through [client] on [scope], and
 * a failure to reach it lands in [status] rather than being thrown. Staleness is judged by the
 * app's clock, estimated from [clock] and the agent's timestamps.
 */
@Stable
internal class SoilCacheBrowser(
    private val client: SoilCacheClient,
    private val scope: CoroutineScope,
    private val clock: Clock,
) : SoilInspectorActions {
    var coverage: SoilCacheCoverage? by mutableStateOf(null)
        private set

    /** In the order the agent first reported them. */
    var listedEntries: List<ListedSoilEntry> by mutableStateOf(emptyList())
        private set

    var selectedHandle: String? by mutableStateOf(null)
        private set

    /** Null while nothing is selected. */
    var selectedValue: SoilValueLoad? by mutableStateOf(null)
        private set

    var status: SoilBrowserStatus? by mutableStateOf(null)
        private set

    /** What happened to the entries, oldest first, since the timeline was last cleared. */
    var events: List<SoilEvent> by mutableStateOf(emptyList())
        private set

    var selectedEventSequence: Long? by mutableStateOf(null)
        private set

    /** When each entry last had an event, by the app's clock; clearing the timeline keeps these. */
    var lastActivityEpochMillisByHandle: Map<String, Long> by mutableStateOf(emptyMap())
        private set

    val selectedEntry: ListedSoilEntry?
        get() = listedEntries.firstOrNull { it.entry.handle == selectedHandle }

    private val adoptionLock = Any()
    private var appliedRevision = -1L
    private var agentClockOffsetMillis = 0L
    private var valueRequestNumber = 0L
    private var lastEventSequence = 0L

    /** The app's clock now, in epoch milliseconds, as far as the agent's last timestamp tells. */
    fun agentNowEpochMillis(): Long = clock.now().toEpochMilliseconds() + agentClockOffsetMillis

    suspend fun load() {
        adopt(client.takeSnapshot())
    }

    /**
     * Takes [snapshot] as the whole cache, unless change events newer than it were already
     * applied — a reload's reply can arrive after them.
     */
    fun adopt(snapshot: SoilCacheSnapshot) {
        synchronized(adoptionLock) {
            if (snapshot.revision < appliedRevision) return
            appliedRevision = snapshot.revision
            agentClockOffsetMillis = snapshot.agentEpochMillis - clock.now().toEpochMilliseconds()
            coverage = snapshot.coverage
            val previousSelection = selectedEntry?.entry
            val reportedHandles = snapshot.entries.mapTo(mutableSetOf(), SoilEntry::handle)
            val droppedMutations = listedEntries.filter { it.entry.handle !in reportedHandles && it.entry.kind == SoilEntryKind.MUTATION }
            replaceListedEntries(snapshot.entries.map { ListedSoilEntry(entry = it, isGone = false) } + droppedMutations.map { it.copy(isGone = true) })
            reloadSelectedValueIfReplyReplaced(previousSelection)
            appendEvents(snapshot.recentEvents)
        }
    }

    /**
     * Applies [changes] unless the snapshot or an event already covered them. Each change on the
     * agent bumps the revision by one, so a revision further ahead means an event never arrived,
     * and a fresh snapshot is taken to make up for it.
     */
    fun adopt(changes: SoilEntriesChanged) {
        synchronized(adoptionLock) {
            if (changes.revision <= appliedRevision) return
            val isEventMissing = changes.revision > appliedRevision + 1
            appliedRevision = changes.revision
            agentClockOffsetMillis = changes.agentEpochMillis - clock.now().toEpochMilliseconds()
            val upsertsByHandle = changes.upserts.associateBy(SoilEntry::handle)
            val removedHandles = changes.removedHandles.toSet()
            val previousSelection = selectedEntry?.entry
            val updated = listedEntries.mapNotNull { listed ->
                val handle = listed.entry.handle
                when {
                    handle in upsertsByHandle -> ListedSoilEntry(entry = upsertsByHandle.getValue(handle), isGone = false)
                    handle !in removedHandles -> listed
                    listed.entry.kind == SoilEntryKind.MUTATION -> listed.copy(isGone = true)
                    else -> null
                }
            }
            val listedHandles = updated.mapTo(mutableSetOf()) { it.entry.handle }
            replaceListedEntries(updated + changes.upserts.filter { it.handle !in listedHandles }.map { ListedSoilEntry(entry = it, isGone = false) })
            reloadSelectedValueIfReplyReplaced(previousSelection)
            appendEvents(changes.events)
            if (isEventMissing) launchReporting(::load)
        }
    }

    override fun refresh() = launchReporting {
        load()
        status = SoilBrowserStatus(message = "Reloaded from the app.", isError = false)
    }

    override fun select(handle: String) {
        if (handle == selectedHandle) return
        if (events.firstOrNull { it.sequence == selectedEventSequence }?.handle != handle) selectedEventSequence = null
        selectedHandle = handle
        selectedValue = SoilValueLoad.Loading
        loadValue(handle)
    }

    override fun reloadSelectedValue() {
        selectedHandle?.let(::loadValue)
    }

    override fun runActionOnSelected(action: SoilEntryAction) {
        val entry = selectedEntry?.entry ?: return
        scope.launch {
            val result = runAction(entry.handle, action)
            status = when (val error = result.error) {
                null -> SoilBrowserStatus(message = "${action.label} ${entry.id.namespace}: done.", isError = false)
                else -> SoilBrowserStatus(message = "${action.label} ${entry.id.namespace}: $error", isError = true)
            }
        }
    }

    override fun selectEvent(sequence: Long) {
        val event = events.firstOrNull { it.sequence == sequence } ?: return
        selectedEventSequence = sequence
        if (listedEntries.any { it.entry.handle == event.handle }) select(event.handle)
    }

    override fun clearEvents() {
        synchronized(adoptionLock) {
            events = emptyList()
            selectedEventSequence = null
        }
    }

    override fun dismissStatus() {
        status = null
    }

    /** Runs [action] on the entry [handle] names; the MCP commands wait on it directly. */
    suspend fun runAction(handle: String, action: SoilEntryAction): SoilEntryActionResult = try {
        client.runAction(handle, action)
    } catch (e: JetWhaleMessagingException) {
        SoilEntryActionResult(error = "failed to reach the app: ${e.message}")
    }

    /** Reads the value of the entry [handle] names; the MCP commands wait on it directly. */
    suspend fun readValue(handle: String): SoilValueLoad = try {
        SoilValueLoad.Loaded(client.readValue(handle))
    } catch (e: JetWhaleMessagingException) {
        SoilValueLoad.Failed("Failed to reach the app: ${e.message}")
    }

    private fun replaceListedEntries(entries: List<ListedSoilEntry>) {
        val goneMutations = entries.filter(ListedSoilEntry::isGone)
        val evictedHandles = goneMutations.take((goneMutations.size - GONE_MUTATION_LIMIT).coerceAtLeast(0)).mapTo(mutableSetOf()) { it.entry.handle }
        listedEntries = entries.filter { it.entry.handle !in evictedHandles }
        val listedHandles = listedEntries.mapTo(mutableSetOf()) { it.entry.handle }
        lastActivityEpochMillisByHandle = lastActivityEpochMillisByHandle.filterKeys(listedHandles::contains)
        if (selectedHandle != null && selectedEntry == null) {
            selectedHandle = null
            selectedValue = null
        }
    }

    private fun appendEvents(newEvents: List<SoilEvent>) {
        val unseen = newEvents.filter { it.sequence > lastEventSequence }
        if (unseen.isEmpty()) return
        lastEventSequence = unseen.last().sequence
        events = (events + unseen).takeLast(EVENT_LIMIT)
        val listedHandles = listedEntries.mapTo(mutableSetOf()) { it.entry.handle }
        lastActivityEpochMillisByHandle = lastActivityEpochMillisByHandle + unseen.filter { it.handle in listedHandles }.associate { it.handle to it.atEpochMillis }
        if (selectedEventSequence != null && events.none { it.sequence == selectedEventSequence }) selectedEventSequence = null
    }

    private fun reloadSelectedValueIfReplyReplaced(previousSelection: SoilEntry?) {
        val currentSelection = selectedEntry?.entry ?: return
        if (previousSelection != null && currentSelection.replyRevision != previousSelection.replyRevision) loadValue(currentSelection.handle)
    }

    private fun loadValue(handle: String) {
        val requestNumber = synchronized(adoptionLock) { ++valueRequestNumber }
        scope.launch {
            val load = readValue(handle)
            synchronized(adoptionLock) {
                if (requestNumber == valueRequestNumber && handle == selectedHandle) selectedValue = load
            }
        }
    }

    private fun launchReporting(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: JetWhaleMessagingException) {
                status = SoilBrowserStatus(message = "Failed to reach the app: ${e.message}", isError = true)
            }
        }
    }
}
