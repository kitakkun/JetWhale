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
        // lastStartedInvocation rather than runningInvocations: a call can finish before this
        // collector is resumed, and a window that missed it would sit on the wrong plugin for the
        // rest of the run. Every started call carries a fresh id, so keying distinctness on the id
        // follows each new call exactly once while leaving the completion updates alone.
        mcpActivityRepository.activityFlow
            .mapNotNull { it.lastStartedInvocation }
            .distinctUntilChangedBy { it.id }
            .collect { invocation -> follow(invocation) }
    }

    private suspend fun follow(invocation: McpToolInvocation) {
        // Read per call, not once: turning the mode off has to stop the next call from moving the
        // window, without restarting the collector.
        if (!debuggerSettingsRepository.followAiOperationEnabledFlow.value) return
        // Host tools (navigation, settings, status) name no plugin, and there is nothing to follow.
        val pluginId = invocation.pluginId ?: return

        val currentView = hostNavigationService.currentView.value
        // Null until the window reports its first destination; navigating then is still right,
        // because the request waits in the channel until the window is there to take it.
        if (currentView != null && currentView.destination.alreadyShows(invocation, pluginId)) return

        hostNavigationService.navigate(HostNavigationRequest.Plugin(pluginId, invocation.sessionId))
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
