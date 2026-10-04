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
        state4 = rememberSubscription(screenContext.hostUpdateStateSubscriptionKey),
    ) { debuggerSettings, appearanceSettings, diagnostics, hostUpdateState ->
        val screenChannel = rememberScreenChannel<GeneralSettingsScreenAction, Nothing>()
        val uiState = context(screenContext.presenterContext) {
            generalSettingsScreenPresenter(
                screenChannel = screenChannel,
                automaticallyWireADBTransport = debuggerSettings.adbAutoPortMappingEnabled,
                followAiOperationEnabled = debuggerSettings.followAiOperationEnabled,
                checkForUpdatesOnStartup = debuggerSettings.checkForUpdatesOnStartup,
                appearanceSettings = appearanceSettings,
                diagnostics = diagnostics,
                hostUpdateState = hostUpdateState,
            )
        }

        GeneralSettingsScreen(
            page = page,
            uiState = uiState,
            modifier = modifier,
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
                } catch (e: IllegalArgumentException) {
                    logger.warning("$path does not exist: ${e.message}")
                } catch (e: SecurityException) {
                    logger.warning("Not allowed to open $path: ${e.message}")
                }
            },
            onClickOpenLogViewer = onOpenLogViewer,
            onFollowAiOperationChange = {
                screenChannel.send(GeneralSettingsScreenAction.ChangeFollowAiOperation(it))
            },
            onCheckForUpdatesOnStartupChange = {
                screenChannel.send(GeneralSettingsScreenAction.ChangeCheckForUpdatesOnStartup(it))
            },
            onClickCheckForUpdates = { screenChannel.send(GeneralSettingsScreenAction.CheckForUpdates) },
            onClickDownloadUpdate = { screenChannel.send(GeneralSettingsScreenAction.DownloadUpdate) },
            onClickCancelUpdateDownload = { screenChannel.send(GeneralSettingsScreenAction.CancelUpdateDownload) },
            onClickRestartToUpdate = { screenChannel.send(GeneralSettingsScreenAction.RestartToUpdate) },
            onClickTryHostVersionAgain = { screenChannel.send(GeneralSettingsScreenAction.TryHostVersionAgain(it)) },
            onClickViewHostLog = { hostUpdateState.setAside?.log?.let { openOnDesktop { open(it.toFile()) } } },
            onClickOpenReleasePage = { version ->
                val page = if (version == null) RELEASES_PAGE else "$RELEASES_PAGE/tag/$version"
                openOnDesktop { browse(URI(page)) }
            },
        )
    }
}

private const val RELEASES_PAGE = "https://github.com/kitakkun/JetWhale/releases"

private fun openOnDesktop(action: Desktop.() -> Unit) {
    try {
        Desktop.getDesktop().action()
    } catch (e: IOException) {
        logger.warning("Could not open it: ${e.message}")
    } catch (e: UnsupportedOperationException) {
        logger.warning("This desktop cannot open it: ${e.message}")
    } catch (e: IllegalArgumentException) {
        logger.warning("It does not exist: ${e.message}")
    }
}

private val logger: Logger = Logger.getLogger("com.kitakkun.jetwhale.host.settings.GeneralSettingsScreenRoot")
