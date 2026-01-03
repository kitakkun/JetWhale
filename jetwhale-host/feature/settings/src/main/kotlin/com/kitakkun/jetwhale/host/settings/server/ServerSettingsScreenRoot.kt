package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberEventFlow
import soil.query.compose.rememberSubscription

context(screenContext: com.kitakkun.jetwhale.host.settings.SettingsScreenContext)
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
