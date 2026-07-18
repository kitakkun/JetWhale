package com.kitakkun.jetwhale.host.settings.general

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext
import soil.query.compose.rememberQuery
import soil.query.compose.rememberSubscription
import java.awt.Desktop

@Composable
context(screenContext: SettingsScreenContext)
fun GeneralSettingsScreenRoot(
    onOpenLogViewer: () -> Unit = {},
) {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.settingsSubscriptionKey),
        state2 = rememberSubscription(screenContext.appearanceSettingsSubscriptionKey),
        state3 = rememberQuery(screenContext.diagnosticsQueryKey),
    ) { debuggerSettings, appearanceSettings, diagnostics ->
        val screenChannel = rememberScreenChannel<GeneralSettingsScreenAction, Nothing>()
        val uiState = context(screenContext.presenterContext) {
            generalSettingsScreenPresenter(
                screenChannel = screenChannel,
                debuggerBehaviorSettings = debuggerSettings,
                appearanceSettings = appearanceSettings,
                diagnostics = diagnostics,
            )
        }

        GeneralSettingsScreen(
            uiState = uiState,
            onCheckedChangePersistData = {
                screenChannel.send(GeneralSettingsScreenAction.ChangePersistData(it))
            },
            onAutomaticallyWireADBTransportChange = {
                screenChannel.send(GeneralSettingsScreenAction.ChangeAutomaticallyWireADBTransport(it))
            },
            onSelectLanguage = {
                screenChannel.send(GeneralSettingsScreenAction.AppLanguageSelected(it))
            },
            onSelectColorScheme = {
                screenChannel.send(GeneralSettingsScreenAction.ColorSchemeSelected(it))
            },
            onClickOpenAppDataPath = {
                val path = uiState.appDataPath.replace("~", System.getProperty("user.home"))
                try {
                    Desktop.getDesktop().open(java.io.File(path))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onClickOpenLogViewer = onOpenLogViewer,
            onClickCheckForUpdates = {
                screenChannel.send(GeneralSettingsScreenAction.CheckForUpdates)
            },
            onClickOpenDownloadPage = { url ->
                try {
                    Desktop.getDesktop().browse(java.net.URI(url))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
        )
    }
}
