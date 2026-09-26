package com.kitakkun.jetwhale.host.data.navigation

import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.FollowAiOperationService
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.model.HostNavigationService
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.McpActivityRepository
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import com.kitakkun.jetwhale.host.model.PluginInstanceService
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
    private val pluginInstanceService: PluginInstanceService,
) : FollowAiOperationService {

    override suspend fun followAiOperations() {
        // lastStartedInvocation rather than runningInvocations: a call can finish before this
        // collector is resumed, and a window that missed it would sit on the wrong plugin for the
        // rest of the run. Every started call carries a fresh id, so keying distinctness on the id
        // follows each new call exactly once while leaving the completion updates alone.
        mcpActivityRepository.activityFlow
            .mapNotNull { it.lastStartedInvocation }
            .distinctUntilChangedBy(McpToolInvocation::id)
            .collect(::follow)
    }

    private suspend fun follow(invocation: McpToolInvocation) {
        // Read per call, not once: turning the mode off has to stop the next call from moving the
        // window, without restarting the collector.
        if (!debuggerSettingsRepository.followAiOperationEnabledFlow.value) return
        // A call that names no plugin (settings, status, listing sessions) has nothing to follow.
        val pluginId = invocation.pluginId ?: return
        // Null until the window reports its first destination; navigating then is still right,
        // because the request waits in the channel until the window is there to take it.
        val currentView = hostNavigationService.currentView.value
        // A call that names no session goes where the window would open it: the host session for a
        // plugin that needs no app, otherwise the app the drawer has selected. The request names that
        // session explicitly, so the window opens exactly the one checked here rather than whatever
        // the drawer selects by the time it runs.
        val targetSessionId = invocation.sessionId
            ?: HostSession.ID.takeIf { pluginInstanceService.getPluginInstanceForSession(pluginId, it) != null }
            ?: currentView?.selectedSessionId
            ?: return
        // A call can name a plugin that has nothing running (switched off, not installed for that
        // session, not started yet): its tool fails, and there is no screen to bring up for it.
        if (pluginInstanceService.getPluginInstanceForSession(pluginId, targetSessionId) == null) return

        if (currentView != null && currentView.destination.alreadyShows(pluginId, targetSessionId)) return

        hostNavigationService.navigate(HostNavigationRequest.Plugin(pluginId, targetSessionId, followsAgent = true))
    }
}

/**
 * Whether the operated plugin is on screen already, either as the main window's destination or in a
 * window of its own. Following in that case would only take the main window off whatever else the
 * user had put there.
 */
private fun HostDestination.alreadyShows(pluginId: String, sessionId: String): Boolean {
    val poppedOut = poppedOutPlugins.any { it.pluginId == pluginId && it.sessionId == sessionId }
    val onScreen = kind == HostDestinationKind.PLUGIN && this.pluginId == pluginId && this.sessionId == sessionId
    return poppedOut || onScreen
}
