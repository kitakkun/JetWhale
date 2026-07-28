package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.kitakkun.jetwhale.host.architecture.ActionResultEffect
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.model.DebugSession
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
