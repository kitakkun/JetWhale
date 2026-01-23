package com.kitakkun.jetwhale.host.settings.general

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberEventFlow
import soil.query.compose.rememberQuery
import soil.query.compose.rememberSubscription
import java.awt.Desktop

context(screenContext: com.kitakkun.jetwhale.host.settings.SettingsScreenContext)
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
                val path = uiState.appDataPath.replace("~", System.getProperty("user.home"))
                try {
                    Desktop.getDesktop().open(java.io.File(path))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )
    }
}
