package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
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

    fun toggleCollapsed(nodeId: String)

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

    override fun refresh(tab: InspectorTab) = launchReporting {
        when (tab) {
            InspectorTab.Coroutines -> tree = client.coroutineTree()
            InspectorTab.Dispatchers -> dispatchers = client.dispatcherStats()
            InspectorTab.Flows -> flows = client.trackedFlows()
            InspectorTab.Dump -> dump = client.dump()
        }
        // A read that succeeds clears an earlier connection error rather than leaving it up.
        if (status?.isError == true) status = null
    }

    override fun toggleCollapsed(nodeId: String) {
        collapsed = if (nodeId in collapsed) collapsed - nodeId else collapsed + nodeId
    }

    override fun clearLongRuns() = launchReporting {
        val cleared = client.clearLongRuns().cleared
        dispatchers = client.dispatcherStats()
        status = InspectorStatus(message = "Cleared $cleared long runs.", isError = false)
    }

    private fun launchReporting(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: JetWhaleMessagingException) {
                status = InspectorStatus(message = "Failed to reach the app: ${e.message}", isError = true)
            }
        }
    }
}
