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
        mcpActivityRepository.activityFlow
            .mapNotNull { it.lastStartedInvocation }
            .distinctUntilChangedBy(McpToolInvocation::id)
            .collect(::follow)
    }

    private suspend fun follow(invocation: McpToolInvocation) {
        if (!debuggerSettingsRepository.followAiOperationEnabledFlow.value) return
        val pluginId = invocation.pluginId ?: return
        val currentView = hostNavigationService.currentView.value
        val targetSessionId = invocation.sessionId
            ?: HostSession.ID.takeIf { pluginInstanceService.getPluginInstanceForSession(pluginId, it) != null }
            ?: currentView?.selectedSessionId
            ?: return
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
