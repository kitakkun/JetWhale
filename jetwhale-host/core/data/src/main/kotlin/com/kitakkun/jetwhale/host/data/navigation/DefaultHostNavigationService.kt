package com.kitakkun.jetwhale.host.data.navigation

import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.model.HostNavigationService
import com.kitakkun.jetwhale.host.model.HostViewState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultHostNavigationService : HostNavigationService {
    // A buffered channel rather than a SharedFlow: a request that arrives before the window has
    // composed has to wait for the collector, not be dropped on the floor.
    private val requestChannel = Channel<HostNavigationRequest>(Channel.BUFFERED)
    override val requests: Flow<HostNavigationRequest> = requestChannel.receiveAsFlow()

    private val destinationFlow = MutableStateFlow<HostDestination?>(null)
    private val selectionFlow = MutableStateFlow(Selection(null, null))

    override val currentView: StateFlow<HostViewState?>
        field = MutableStateFlow<HostViewState?>(null)

    override suspend fun navigate(request: HostNavigationRequest) {
        requestChannel.send(request)
    }

    override fun updateDestination(destination: HostDestination) {
        destinationFlow.value = destination
        recomputeCurrentView()
    }

    override fun updateSelection(selectedSessionId: String?, selectedPluginId: String?) {
        selectionFlow.value = Selection(selectedSessionId, selectedPluginId)
        recomputeCurrentView()
    }

    private fun recomputeCurrentView() {
        val destination = destinationFlow.value ?: return
        val selection = selectionFlow.value
        currentView.value = HostViewState(
            destination = destination,
            selectedSessionId = selection.sessionId,
            selectedPluginId = selection.pluginId,
        )
    }

    private data class Selection(val sessionId: String?, val pluginId: String?)
}
