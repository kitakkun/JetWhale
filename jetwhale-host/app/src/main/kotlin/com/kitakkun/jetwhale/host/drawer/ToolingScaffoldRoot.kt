package com.kitakkun.jetwhale.host.drawer

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberEventFlow
import soil.query.compose.rememberSubscription

context(screenContext: ToolingScaffoldScreenContext)
@Composable
fun ToolingScaffoldRoot(
    onClickSettings: () -> Unit,
    onClickInfo: () -> Unit,
    onClickPlugin: (pluginId: String, sessionId: String) -> Unit,
    onClickPopout: (pluginId: String, sessionId: String) -> Unit,
    content: @Composable () -> Unit,
) {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.loadedPluginsMetaDataSubscriptionKey),
        state2 = rememberSubscription(screenContext.debugSessionsSubscriptionKey),
    ) { loadedPlugins, debugSessions ->
        val eventFlow = rememberEventFlow<ToolingScaffoldEvent>()
        val uiState = toolingScaffoldPresenter(eventFlow, loadedPlugins, debugSessions)

        ToolingScaffold(
            uiState = uiState,
            onClickSettings = onClickSettings,
            onClickInfo = onClickInfo,
            onClickPlugin = {
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold
                eventFlow.tryEmit(ToolingScaffoldEvent.UpdateSelectedPlugin(it))
                onClickPlugin(it, selectedSession.id)
            },
            onClickPopout = {
                val selectedSession = uiState.selectedSession ?: return@ToolingScaffold
                onClickPopout(it.id, selectedSession.id)
            },
            onSelectSession = { eventFlow.tryEmit(ToolingScaffoldEvent.SelectSession(it)) },
            content = content,
        )
    }
}
