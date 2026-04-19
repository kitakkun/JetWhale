package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberEventFlow
import soil.query.compose.rememberSubscription

@Composable
context(screenContext: com.kitakkun.jetwhale.host.settings.SettingsScreenContext)
fun ServerSettingsScreenRoot() {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.serverStatusSubscriptionKey),
        state2 = rememberSubscription(screenContext.mcpServerStatusSubscriptionKey),
        state3 = rememberSubscription(screenContext.settingsSubscriptionKey),
    ) { serverStatus, mcpServerStatus, debuggerSettings ->
        val eventFlow = rememberEventFlow<ServerSettingsScreenEvent>()
        val uiState = serverSettingsScreenPresenter(
            eventFlow = eventFlow,
            serverStatus = serverStatus,
            mcpServerStatus = mcpServerStatus,
            debuggerSettings = debuggerSettings,
        )

        ServerSettingsScreen(
            uiState = uiState,
            onDebugPortTextChange = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.ChangeDebugPortText(it))
            },
            onApplyDebugPortChange = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.ApplyDebugPortChange)
            },
            onConfirmApplyDebugPortChange = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.ConfirmApplyDebugPortChange)
            },
            onDismissApplyDebugPortDialog = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.DismissApplyDebugPortDialog)
            },
            onMcpPortTextChange = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.ChangeMcpPortText(it))
            },
            onApplyMcpPortChange = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.ApplyMcpPortChange)
            },
            onConfirmApplyMcpPortChange = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.ConfirmApplyMcpPortChange)
            },
            onDismissApplyMcpPortDialog = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.DismissApplyMcpPortDialog)
            },
        )
    }
}
