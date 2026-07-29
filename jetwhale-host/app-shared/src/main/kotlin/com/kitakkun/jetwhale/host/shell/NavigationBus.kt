package com.kitakkun.jetwhale.host.shell

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.model.HostViewState
import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** A plugin screen asked for from outside the composition, by the MCP server. */
data class ExternalPluginRequest(val pluginId: String, val sessionId: String?)

/**
 * The single origin of host navigation: every navigator sends its commands here, and the navigation
 * host is the only collector, so the back stack has exactly one writer.
 *
 * It also mirrors the read side. The navigation host publishes the back stack back into this bus
 * after every change, which is what lets a navigator answer questions about the current state — and
 * what lets a caller outside the composition confirm that its own request was applied.
 *
 * The channels are unbounded so sending never suspends and a command sent before the window has
 * composed waits for the collector rather than being dropped.
 */
@Inject
@SingleIn(AppScope::class)
class NavigationBus {
    private val commandChannel = Channel<NavCommand>(Channel.UNLIMITED)
    private val pluginRequestChannel = Channel<ExternalPluginRequest>(Channel.UNLIMITED)
    private val selectionFlow = MutableStateFlow(Selection(sessionId = null, pluginId = null))

    /** Delivered exactly once each, so this must have a single collector: the navigation host. */
    val commands: Flow<NavCommand> = commandChannel.receiveAsFlow()

    /**
     * Plugin screens asked for from outside the composition. They are not plain commands because
     * opening a plugin also moves the drawer's session and plugin selection, which only the drawer
     * can do. Delivered exactly once each, to the drawer.
     */
    val externalPluginRequests: Flow<ExternalPluginRequest> = pluginRequestChannel.receiveAsFlow()

    /** Null until the host window has composed and published its back stack. */
    val backStack: StateFlow<List<NavKey>?>
        field = MutableStateFlow<List<NavKey>?>(null)

    val poppedOutPlugins: StateFlow<List<PoppedOutPlugin>>
        field = MutableStateFlow<List<PoppedOutPlugin>>(emptyList())

    /** Null until the host window has composed and published its back stack. */
    val currentView: StateFlow<HostViewState?>
        field = MutableStateFlow<HostViewState?>(null)

    fun send(command: NavCommand) {
        commandChannel.trySend(command)
    }

    fun requestPlugin(request: ExternalPluginRequest) {
        pluginRequestChannel.trySend(request)
    }

    fun publishBackStack(keys: List<NavKey>) {
        backStack.value = keys
        poppedOutPlugins.value = keys.poppedOutPlugins()
        recomputeCurrentView()
    }

    fun updateSelection(selectedSessionId: String?, selectedPluginId: String?) {
        selectionFlow.value = Selection(sessionId = selectedSessionId, pluginId = selectedPluginId)
        recomputeCurrentView()
    }

    private fun recomputeCurrentView() {
        val keys = backStack.value ?: return
        val selection = selectionFlow.value
        currentView.value = HostViewState(
            destination = keys.toHostDestination(),
            selectedSessionId = selection.sessionId,
            selectedPluginId = selection.pluginId,
        )
    }

    private data class Selection(val sessionId: String?, val pluginId: String?)
}
