package com.kitakkun.jetwhale.host.data.navigation

import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.FollowAiOperationService
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.model.HostNavigationService
import com.kitakkun.jetwhale.host.model.McpActivityRepository
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultFollowAiOperationService(
    private val mcpActivityRepository: McpActivityRepository,
    private val debuggerSettingsRepository: DebuggerSettingsRepository,
    private val hostNavigationService: HostNavigationService,
) : FollowAiOperationService {

    override suspend fun followAiOperations() {
        mcpActivityRepository.activityFlow
            .mapNotNull { it.lastStartedInvocation }
            .distinctUntilChangedBy(McpToolInvocation::id)
            .collect(::follow)
    }

    private suspend fun follow(invocation: McpToolInvocation) {
        if (!debuggerSettingsRepository.followAiOperationEnabledFlow.value) return
        val pluginId = invocation.pluginId ?: return

        val currentView = hostNavigationService.currentView.value
        if (currentView != null && currentView.destination.alreadyShows(invocation, pluginId)) return

        hostNavigationService.navigate(HostNavigationRequest.Plugin(pluginId, invocation.sessionId, followsAgent = true))
    }
}

/**
 * Whether the operated plugin is on screen already, either as the main window's destination or in a
 * window of its own. Following in that case would only take the main window off whatever else the
 * user had put there.
 *
 * A call that names no session is followed against whatever session the drawer has selected, so it
 * counts as shown wherever that plugin is shown.
 */
private fun HostDestination.alreadyShows(invocation: McpToolInvocation, pluginId: String): Boolean {
    val matchesSession = { sessionId: String? -> invocation.sessionId == null || invocation.sessionId == sessionId }
    val poppedOut = poppedOutPlugins.any { it.pluginId == pluginId && matchesSession(it.sessionId) }
    val onScreen = kind == HostDestinationKind.PLUGIN && this.pluginId == pluginId && matchesSession(this.sessionId)
    return poppedOut || onScreen
}
