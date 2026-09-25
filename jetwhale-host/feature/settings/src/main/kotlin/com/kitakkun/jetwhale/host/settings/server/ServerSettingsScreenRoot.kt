package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.model.DebugServerSettings
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import soil.query.compose.rememberSubscription
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.util.logging.Logger

private const val MCP_GUIDE_URL = "https://kitakkun.github.io/JetWhale/guide/mcp-server"

@Composable
context(screenContext: SettingsScreenContext)
fun ServerSettingsScreenRoot(page: SettingsScreenPage) {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.serverStatusSubscriptionKey),
        state2 = rememberSubscription(screenContext.mcpServerStatusSubscriptionKey),
        state3 = rememberSubscription(screenContext.settingsSubscriptionKey),
        state4 = rememberSubscription(screenContext.sslCertificatesSubscriptionKey),
        state5 = rememberSubscription(screenContext.mcpPermissionsSnapshotSubscriptionKey),
    ) { serverStatus, mcpServerStatus, debuggerSettings, sslCertificates, mcpPermissionsSnapshot ->
        val screenChannel = rememberScreenChannel<ServerSettingsScreenAction, Nothing>()
        val uiState = context(screenContext.presenterContext) {
            serverSettingsScreenPresenter(
                screenChannel = screenChannel,
                serverStatus = serverStatus,
                mcpServerStatus = mcpServerStatus,
                debugServerSettings = DebugServerSettings(
                    serverPort = debuggerSettings.serverPort,
                    wssPort = debuggerSettings.wssPort,
                    wssEnabled = debuggerSettings.wssEnabled,
                ),
                mcpServerPort = debuggerSettings.mcpServerPort,
                sslCertificates = sslCertificates,
                mcpPermissionsSnapshot = mcpPermissionsSnapshot,
            )
        }

        ServerSettingsScreen(
            page = page,
            uiState = uiState,
            onDebugPortTextChange = { screenChannel.send(ServerSettingsScreenAction.ChangeDebugPortText(it)) },
            onWssPortTextChange = { screenChannel.send(ServerSettingsScreenAction.ChangeWssPortText(it)) },
            onWssEnabledChange = { screenChannel.send(ServerSettingsScreenAction.ChangeWssEnabled(it)) },
            onApplyDebugServerSettingsChange = { screenChannel.send(ServerSettingsScreenAction.ApplyDebugServerSettingsChange) },
            onConfirmApplyDebugServerSettingsChange = { screenChannel.send(ServerSettingsScreenAction.ConfirmApplyDebugServerSettingsChange) },
            onDismissApplyDebugServerSettingsDialog = { screenChannel.send(ServerSettingsScreenAction.DismissApplyDebugServerSettingsDialog) },
            onMcpPortTextChange = { screenChannel.send(ServerSettingsScreenAction.ChangeMcpPortText(it)) },
            onApplyMcpPortChange = { screenChannel.send(ServerSettingsScreenAction.ApplyMcpPortChange) },
            onConfirmApplyMcpPortChange = { screenChannel.send(ServerSettingsScreenAction.ConfirmApplyMcpPortChange) },
            onDismissApplyMcpPortDialog = { screenChannel.send(ServerSettingsScreenAction.DismissApplyMcpPortDialog) },
            onClickOpenMcpGuide = {
                try {
                    Desktop.getDesktop().browse(URI(MCP_GUIDE_URL))
                } catch (e: IOException) {
                    logger.warning("Could not open $MCP_GUIDE_URL: ${e.message}")
                } catch (e: UnsupportedOperationException) {
                    logger.warning("This desktop cannot open links: ${e.message}")
                } catch (e: SecurityException) {
                    logger.warning("Not allowed to open $MCP_GUIDE_URL: ${e.message}")
                }
            },
            onSetHostGroupAllowed = { group, allowed -> screenChannel.send(ServerSettingsScreenAction.SetHostGroupAllowed(group, allowed)) },
            onSetPluginInspectAllowed = { pluginId, allowed -> screenChannel.send(ServerSettingsScreenAction.SetPluginInspectAllowed(pluginId, allowed)) },
            onSetPluginInteractAllowed = { pluginId, allowed -> screenChannel.send(ServerSettingsScreenAction.SetPluginInteractAllowed(pluginId, allowed)) },
            onSetPluginToolAllowed = { toolName, allowed -> screenChannel.send(ServerSettingsScreenAction.SetPluginToolAllowed(toolName, allowed)) },
            onAddCertificate = { screenChannel.send(ServerSettingsScreenAction.AddCertificate) },
            onSetActiveCertificate = { screenChannel.send(ServerSettingsScreenAction.SetActiveCertificate(it)) },
            onDeleteCertificate = { screenChannel.send(ServerSettingsScreenAction.DeleteCertificate(it)) },
            onShowCertificateDetail = { screenChannel.send(ServerSettingsScreenAction.ShowCertificateDetail(it)) },
            onDismissCertificateDetailDialog = { screenChannel.send(ServerSettingsScreenAction.DismissCertificateDetailDialog) },
        )
    }
}

private val logger: Logger = Logger.getLogger("com.kitakkun.jetwhale.host.settings.ServerSettingsScreenRoot")
