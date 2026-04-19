package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberEventFlow
import soil.query.compose.rememberSubscription

@Composable
context(screenContext: ToolingScaffoldScreenContext)
fun ToolingScaffoldRoot(
    onClickSettings: () -> Unit,
    onClickPluginSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (pluginId: String, sessionId: String) -> Unit,
    onClickPopout: (pluginId: String, pluginName: String, sessionId: String) -> Unit,
    content: @Composable () -> Unit,
) {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.loadedPluginsMetaDataSubscriptionKey),
        state2 = rememberSubscription(screenContext.debugSessionsSubscriptionKey),
        state3 = rememberSubscription(screenContext.enabledPluginsSubscriptionKey),
        state4 = rememberSubscription(screenContext.failedPluginJarPathsSubscriptionKey),
    ) { loadedPlugins, debugSessions, enabledPluginIds, failedJarPaths ->
        val eventFlow = rememberEventFlow<ToolingScaffoldEvent>()
        val uiState = toolingScaffoldPresenter(
            eventFlow = eventFlow,
            loadedPlugins = loadedPlugins,
            debugSessions = debugSessions,
            enabledPluginIds = enabledPluginIds,
            hasFailedJars = failedJarPaths.isNotEmpty(),
        )

        ToolingScaffold(
            uiState = uiState,
            onClickSettings = onClickSettings,
            onClickPluginSettings = onClickPluginSettings,
            onClickInfo = onClickInfo,
            onClickPlugin = {
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold
                eventFlow.tryEmit(ToolingScaffoldEvent.UpdateSelectedPlugin(it))
                onClickPlugin(it, selectedSession.id)
            },
            onClickPopout = {
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold
                onClickPopout(it.id, it.name, selectedSession.id)
            },
            onSelectSession = { eventFlow.tryEmit(ToolingScaffoldEvent.SelectSession(it)) },
            onSetPluginEnabled = { pluginId, enabled ->
                eventFlow.tryEmit(ToolingScaffoldEvent.SetPluginEnabled(pluginId, enabled))
            },
            content = content,
        )
    }
}
