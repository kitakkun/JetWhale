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
import kotlinx.coroutines.CancellationException
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

    /** Shows the coroutine with [coroutineId], a [CoroutineNode.id], in the detail pane, or nothing when null. */
    fun select(coroutineId: String?)

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

    var collapsedRowIds: Set<String> by mutableStateOf(emptySet())
        private set

    var dispatcherStatsReport: DispatcherStatsReport? by mutableStateOf(null)
        private set

    var trackedFlowReport: TrackedFlowReport? by mutableStateOf(null)
        private set

    var dump: CoroutineDump? by mutableStateOf(null)
        private set

    var status: InspectorStatus? by mutableStateOf(null)
        private set

    var selectedCoroutineId: String? by mutableStateOf(null)
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

    private val refreshJobs = mutableMapOf<InspectorTab, Job>()

    private var longRunClears = 0

    override fun refresh(tab: InspectorTab) {
        if (refreshJobs[tab]?.isActive == true) return
        refreshJobs[tab] = launchReporting { read(tab) }
    }

    private suspend fun read(tab: InspectorTab) {
        when (tab) {
            InspectorTab.Coroutines -> {
                val latestTree = client.coroutineTree()
                tree = latestTree
                selectedCoroutineId?.let { findCoroutine(latestTree.roots, it) }?.let { lastSeenSelection = it }
            }

            InspectorTab.Dispatchers -> {
                val clearsBeforeRead = longRunClears
                val report = client.dispatcherStats()
                if (clearsBeforeRead == longRunClears) dispatcherStatsReport = report
            }

            InspectorTab.Flows -> trackedFlowReport = client.trackedFlows()

            InspectorTab.Dump -> dump = client.dumpCoroutines()
        }
        if (status?.isError == true) status = null
    }

    override fun toggleCollapsed(rowId: String) {
        collapsedRowIds = if (rowId in collapsedRowIds) collapsedRowIds - rowId else collapsedRowIds + rowId
    }

    override fun select(coroutineId: String?) {
        selectedCoroutineId = coroutineId
        detail = null
        lastSeenSelection = coroutineId?.let { tree?.let { current -> findCoroutine(current.roots, it) } }
        coroutineId?.let(::readDetail)
    }

    override fun reloadDetail() {
        selectedCoroutineId?.let(::readDetail)
    }

    private fun readDetail(id: String) {
        launchReporting {
            val latestDetail = client.coroutineDetail(id)
            if (selectedCoroutineId == id) detail = latestDetail
        }
    }

    override fun clearLongRuns() {
        launchReporting {
            val cleared = client.clearLongRuns().cleared
            longRunClears++
            dispatcherStatsReport = client.dispatcherStats()
            status = InspectorStatus(message = "Cleared $cleared long runs.", isError = false)
        }
    }

    private fun launchReporting(block: suspend () -> Unit): Job = scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: JetWhaleMessagingException) {
            status = InspectorStatus(message = "Failed to reach the app: ${e.message}", isError = true)
        } catch (e: Exception) {
            status = InspectorStatus(message = "Unexpected error: $e", isError = true)
        }
    }
}
