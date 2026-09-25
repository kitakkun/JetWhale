package com.kitakkun.jetwhale.host.settings.general

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import soil.query.compose.rememberQuery
import soil.query.compose.rememberSubscription
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.logging.Logger

@Composable
context(screenContext: SettingsScreenContext)
fun GeneralSettingsScreenRoot(
    page: SettingsScreenPage,
    onOpenLogViewer: () -> Unit,
    modifier: Modifier = Modifier,
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
                automaticallyWireADBTransport = debuggerSettings.adbAutoPortMappingEnabled,
                checkForUpdatesOnStartup = debuggerSettings.checkForUpdatesOnStartup,
                followAiOperationEnabled = debuggerSettings.followAiOperationEnabled,
                appearanceSettings = appearanceSettings,
                diagnostics = diagnostics,
            )
        }

        GeneralSettingsScreen(
            page = page,
            uiState = uiState,
            modifier = modifier,
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
                    Desktop.getDesktop().open(File(path))
                } catch (e: IOException) {
                    logger.warning("Could not open $path: ${e.message}")
                } catch (e: UnsupportedOperationException) {
                    logger.warning("This desktop cannot open folders: ${e.message}")
                }
            },
            onClickOpenLogViewer = onOpenLogViewer,
            onClickCheckForUpdates = {
                screenChannel.send(GeneralSettingsScreenAction.CheckForUpdates)
            },
            onCheckForUpdatesOnStartupChange = {
                screenChannel.send(GeneralSettingsScreenAction.ChangeCheckForUpdatesOnStartup(it))
            },
            onFollowAiOperationChange = {
                screenChannel.send(GeneralSettingsScreenAction.ChangeFollowAiOperation(it))
            },
            onClickInstallUpdate = {
                screenChannel.send(GeneralSettingsScreenAction.InstallUpdate)
            },
            onClickOpenDownloadPage = { url ->
                try {
                    Desktop.getDesktop().browse(URI(url))
                } catch (e: IOException) {
                    logger.warning("Could not open $url: ${e.message}")
                } catch (e: UnsupportedOperationException) {
                    logger.warning("This desktop cannot open links: ${e.message}")
                }
            },
        )
    }
}

private val logger: Logger = Logger.getLogger("com.kitakkun.jetwhale.host.settings.GeneralSettingsScreenRoot")
