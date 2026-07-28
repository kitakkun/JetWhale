package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.kitakkun.jetwhale.host.architecture.ActionResultEffect
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.navigation.toSegmentedMenu
import com.kitakkun.jetwhale.host.settings.SettingsScreenSegmentedMenu
import soil.query.compose.rememberSubscription

@Composable
context(screenContext: ToolingScaffoldScreenContext)
fun ToolingScaffoldRoot(
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (pluginId: String, sessionId: String) -> Unit,
    onOpenMcpTools: (pluginId: String?, sessionId: String?) -> Unit,
    onClickPopout: (pluginId: String, pluginName: String, sessionId: String) -> Unit,
    isPoppedOut: (pluginId: String, sessionId: String) -> Boolean,
    onClickBringBack: (pluginId: String, sessionId: String) -> Unit,
    onSelectedSessionChange: (selectedSession: DebugSession) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSettings: (SettingsScreenSegmentedMenu) -> Unit,
    onNavigateLogViewer: () -> Unit,
    content: @Composable () -> Unit,
) {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.loadedPluginsMetaDataSubscriptionKey),
        state2 = rememberSubscription(screenContext.debugSessionsSubscriptionKey),
        state3 = rememberSubscription(screenContext.enabledPluginsSubscriptionKey),
        state4 = rememberSubscription(screenContext.failedPluginJarPathsSubscriptionKey),
        state5 = rememberSubscription(screenContext.mcpActivitySubscriptionKey),
        state6 = rememberSubscription(screenContext.mcpCapablePluginsSubscriptionKey),
    ) { loadedPlugins, debugSessions, enabledPluginIds, failedJars, mcpActivity, mcpCapablePlugins ->
        val screenChannel = rememberScreenChannel<ToolingScaffoldScreenAction, ToolingScaffoldScreenActionResult>()
        ActionResultEffect(screenChannel) { result ->
            when (result) {
                // No message sink yet; results are routed through the channel so a future
                // Root-side handler (e.g. a Snackbar) can surface them during the rollout.
                is ToolingScaffoldScreenActionResult.SessionClosed -> Unit

                is ToolingScaffoldScreenActionResult.SetPluginEnabledFailed -> Unit
            }
        }
        val uiState = context(screenContext.presenterContext) {
            toolingScaffoldPresenter(
                screenChannel = screenChannel,
                loadedPlugins = loadedPlugins,
                debugSessions = debugSessions,
                enabledPluginIds = enabledPluginIds,
                hasFailedJars = failedJars.isNotEmpty(),
                mcpActivity = mcpActivity,
                mcpCapablePlugins = mcpCapablePlugins,
            )
        }

        // When the active/selected session changes, notify the host so that an open plugin
        // screen can follow the newly-selected session instead of lingering on the old one.
        LaunchedEffect(uiState.selectedSessionId) {
            val selectedSession = uiState.selectedSession ?: return@LaunchedEffect
            onSelectedSessionChange(selectedSession)
        }

        // Publish the drawer's selection so a caller outside the composition (the MCP server) can
        // read what the window is pointed at; the destination itself is published by JetWhaleApp.
        LaunchedEffect(uiState.selectedSessionId, uiState.selectedPluginId) {
            screenContext.hostNavigationService.updateSelection(
                selectedSessionId = uiState.selectedSessionId.takeIf { it.isNotEmpty() },
                selectedPluginId = uiState.selectedPluginId.takeIf { it.isNotEmpty() },
            )
        }

        // The collector below outlives every recomposition, so it must not close over the sessions
        // and ui state of the composition that started it.
        val currentSessions by rememberUpdatedState(debugSessions)
        val currentUiState by rememberUpdatedState(uiState)

        // The single collector of navigation requests: only here are both the screen channel and
        // the navigation callbacks in scope, and the request channel delivers each request once.
        LaunchedEffect(screenChannel) {
            screenContext.hostNavigationService.requests.collect { request ->
                when (request) {
                    HostNavigationRequest.Home -> onNavigateHome()

                    HostNavigationRequest.Info -> onClickInfo()

                    HostNavigationRequest.LogViewer -> onNavigateLogViewer()

                    is HostNavigationRequest.Settings -> onNavigateSettings(request.section.toSegmentedMenu())

                    is HostNavigationRequest.Plugin -> {
                        // Only a request that named no session falls back to the drawer's selection.
                        // A named session that has gone away since the request was validated must
                        // drop the request rather than navigate to some other app.
                        val targetSession = when (val requestedSessionId = request.sessionId) {
                            null -> currentUiState.selectedSession
                            else -> currentSessions.firstOrNull { it.id == requestedSessionId }
                        } ?: return@collect
                        // Drive the same path a drawer click takes, so an MCP-driven navigation and a
                        // click are indistinguishable downstream.
                        if (targetSession.id != currentUiState.selectedSessionId) {
                            screenChannel.send(ToolingScaffoldScreenAction.SelectSession(targetSession))
                        }
                        screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(request.pluginId))
                        onClickPlugin(request.pluginId, targetSession.id)
                    }
                }
            }
        }

        ToolingScaffold(
            uiState = uiState,
            onClickSettings = onClickSettings,
            onClickPluginSettings = onClickPluginSettings,
            onClickInfo = onClickInfo,
            onClickPlugin = {
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold
                screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(it))
                onClickPlugin(it, selectedSession.id)
            },
            // The browser tolerates a missing session, so the badge stays usable while no session
            // is selected: it simply opens with the session filter on "All".
            onOpenMcpTools = { onOpenMcpTools(it, uiState.selectedSession?.id) },
            onOpenAllMcpTools = { onOpenMcpTools(null, null) },
            onClickPopout = {
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold
                onClickPopout(it.id, it.name, selectedSession.id)
            },
            isPoppedOut = { pluginId ->
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold false
                isPoppedOut(pluginId, selectedSession.id)
            },
            onClickBringBack = {
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold
                screenChannel.send(ToolingScaffoldScreenAction.UpdateSelectedPlugin(it.id))
                onClickBringBack(it.id, selectedSession.id)
            },
            onSelectSession = { screenChannel.send(ToolingScaffoldScreenAction.SelectSession(it)) },
            onSetPluginEnabled = { pluginId, enabled ->
                screenChannel.send(ToolingScaffoldScreenAction.SetPluginEnabled(pluginId, enabled))
            },
            content = content,
        )
    }
}
