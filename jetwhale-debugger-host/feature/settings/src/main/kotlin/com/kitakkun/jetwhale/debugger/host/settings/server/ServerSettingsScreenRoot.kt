package com.kitakkun.jetwhale.debugger.host.settings.server

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.debugger.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.debugger.host.architecture.rememberEventFlow
import com.kitakkun.jetwhale.debugger.host.settings.SettingsScreenContext
import soil.query.compose.rememberSubscription

context(screenContext: SettingsScreenContext)
@Composable
fun ServerSettingsScreenRoot() {
    SoilDataBoundary(
        state = rememberSubscription(screenContext.serverStatusSubscriptionKey),
    ) { serverStatus ->
        val eventFlow = rememberEventFlow<ServerSettingsScreenEvent>()
        val uiState = serverSettingsScreenPresenter(
            eventFlow = eventFlow,
            serverStatus = serverStatus,
        )

        ServerSettingsScreen(uiState = uiState)
    }
}
