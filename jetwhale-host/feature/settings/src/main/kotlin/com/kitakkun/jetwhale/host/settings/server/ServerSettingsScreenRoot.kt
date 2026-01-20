package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberEventFlow
import soil.query.compose.rememberSubscription

context(screenContext: com.kitakkun.jetwhale.host.settings.SettingsScreenContext)
@Composable
fun ServerSettingsScreenRoot() {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.serverStatusSubscriptionKey),
        state2 = rememberSubscription(screenContext.settingsSubscriptionKey),
    ) { serverStatus, debuggerSettings ->
        val eventFlow = rememberEventFlow<ServerSettingsScreenEvent>()
        val uiState = serverSettingsScreenPresenter(
            eventFlow = eventFlow,
            serverStatus = serverStatus,
            debuggerSettings = debuggerSettings,
        )

        ServerSettingsScreen(
            uiState = uiState,
            onPortTextChange = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.ChangePortText(it))
            },
            onApplyPortChange = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.ApplyPortChange)
            },
            onConfirmApplyPortChange = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.ConfirmApplyPortChange)
            },
            onDismissApplyPortDialog = {
                eventFlow.tryEmit(ServerSettingsScreenEvent.DismissApplyPortDialog)
            },
        )
    }
}
