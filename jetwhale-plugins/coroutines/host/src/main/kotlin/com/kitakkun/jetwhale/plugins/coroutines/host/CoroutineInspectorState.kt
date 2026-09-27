package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class InspectorTab(val label: String) {
    Coroutines("Coroutines"),
    Dispatchers("Dispatchers"),
    Flows("Flows"),
    Dump("Dump"),
}

internal data class InspectorStatus(val message: String, val isError: Boolean)

/** What the user does in the inspector's UI. */
internal interface CoroutineInspectorActions {
    /** Reads what [tab] shows from the app again. */
    fun refresh(tab: InspectorTab)

    /** Collapses or expands the row with [rowId], a [CoroutineRow.rowId]. */
    fun toggleCollapsed(rowId: String)

    /** Shows the coroutine with [id], a [CoroutineNode.id], in the detail pane, or nothing when null. */
    fun select(id: String?)

    /** Reads the selected coroutine's stacks from the app again. */
    fun reloadDetail()

    fun clearLongRuns()
}

/**
 * The latest reports read from the app, one per tab. The UI refreshes the visible tab on a timer
 * while it is shown, so nothing is read while no one is looking. A failure lands in [status].
 */
@Stable
internal class CoroutineInspectorState(
    private val client: CoroutineInspectorClient,
    private val scope: CoroutineScope,
) : CoroutineInspectorActions {
    var tree: CoroutineTree? by mutableStateOf(null)
        private set

    var collapsed: Set<String> by mutableStateOf(emptySet())
        private set

    var dispatchers: DispatcherStatsReport? by mutableStateOf(null)
        private set

    var flows: TrackedFlowReport? by mutableStateOf(null)
        private set

    var dump: CoroutineDump? by mutableStateOf(null)
        private set

    var status: InspectorStatus? by mutableStateOf(null)
        private set

    var selectedId: String? by mutableStateOf(null)
        private set

    /**
     * Where the selected coroutine was in the latest tree that had it: kept after it disappears, so
     * the detail pane can still say what it was.
     */
    var lastSeenSelection: CoroutineLocation? by mutableStateOf(null)
        private set

    /** The selected coroutine's stacks, read when it is selected rather than on the tree's timer. */
    var detail: CoroutineDetail? by mutableStateOf(null)
        private set

    private val refreshes = mutableMapOf<InspectorTab, Job>()

    // Bumped when the dispatcher report changes under a read already in flight (clearing long
    // runs), so that read's older answer is dropped instead of undoing the change.
    private var dispatcherGeneration = 0

    override fun refresh(tab: InspectorTab) {
        // A read slower than the refresh timer is left to finish rather than joined by another;
        // queued reads could only land out of order.
        if (refreshes[tab]?.isActive == true) return
        refreshes[tab] = launchReporting { read(tab) }
    }

    private suspend fun read(tab: InspectorTab) {
        when (tab) {
            InspectorTab.Coroutines -> {
                val read = client.coroutineTree()
                tree = read
                selectedId?.let { findCoroutine(read.roots, it) }?.let { lastSeenSelection = it }
            }

            InspectorTab.Dispatchers -> {
                val generation = dispatcherGeneration
                val report = client.dispatcherStats()
                if (generation == dispatcherGeneration) dispatchers = report
            }

            InspectorTab.Flows -> flows = client.trackedFlows()

            InspectorTab.Dump -> dump = client.dump()
        }
        // A read that succeeds clears an earlier connection error rather than leaving it up.
        if (status?.isError == true) status = null
    }

    override fun toggleCollapsed(rowId: String) {
        collapsed = if (rowId in collapsed) collapsed - rowId else collapsed + rowId
    }

    override fun select(id: String?) {
        selectedId = id
        detail = null
        lastSeenSelection = id?.let { tree?.let { current -> findCoroutine(current.roots, it) } }
        id?.let(::readDetail)
    }

    override fun reloadDetail() {
        selectedId?.let(::readDetail)
    }

    private fun readDetail(id: String) {
        launchReporting {
            val read = client.coroutineDetail(id)
            // Another coroutine may have been selected while this one was read.
            if (selectedId == id) detail = read
        }
    }

    override fun clearLongRuns() {
        launchReporting {
            val cleared = client.clearLongRuns().cleared
            dispatcherGeneration++
            dispatchers = client.dispatcherStats()
            status = InspectorStatus(message = "Cleared $cleared long runs.", isError = false)
        }
    }

    private fun launchReporting(block: suspend () -> Unit): Job = scope.launch {
        try {
            block()
        } catch (e: JetWhaleMessagingException) {
            status = InspectorStatus(message = "Failed to reach the app: ${e.message}", isError = true)
        }
    }
}
