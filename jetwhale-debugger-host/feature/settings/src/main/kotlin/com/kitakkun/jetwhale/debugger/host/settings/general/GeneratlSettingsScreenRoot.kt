package com.kitakkun.jetwhale.debugger.host.settings.general

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.kitakkun.jetwhale.debugger.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.debugger.host.architecture.rememberEventFlow
import com.kitakkun.jetwhale.debugger.host.settings.SettingsScreenContext
import soil.query.compose.rememberQuery
import soil.query.compose.rememberSubscription

context(screenContext: SettingsScreenContext)
@Composable
fun GeneralSettingsScreenRoot() {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.settingsSubscriptionKey),
        state2 = rememberSubscription(screenContext.appearanceSettingsSubscriptionKey),
        state3 = rememberQuery(screenContext.diagnosticsQueryKey),
    ) { debuggerSettings, appearanceSettings, diagnostics ->
        val eventFlow = rememberEventFlow<GeneralSettingsScreenEvent>()
        val uiState = generalSettingsScreenPresenter(
            eventFlow = eventFlow,
            debuggerBehaviorSettings = debuggerSettings,
            appearanceSettings = appearanceSettings,
            diagnostics = diagnostics,
        )
        val uriHandler = LocalUriHandler.current

        GeneralSettingsScreen(
            uiState = uiState,
            onCheckedChangePersistData = {
                eventFlow.tryEmit(GeneralSettingsScreenEvent.ChangePersistData(it))
            },
            onAutomaticallyWireADBTransportChange = {
                eventFlow.tryEmit(GeneralSettingsScreenEvent.ChangeAutomaticallyWireADBTransport(it))
            },
            onSelectLanguage = {
                eventFlow.tryEmit(GeneralSettingsScreenEvent.AppLanguageSelected(it))
            },
            onSelectColorScheme = {
                eventFlow.tryEmit(GeneralSettingsScreenEvent.ColorSchemeSelected(it))
            },
            onClickOpenAppDataPath = {
                // FIXME: Crash
                uriHandler.openUri(uiState.appDataPath)
            }
        )
    }
}
