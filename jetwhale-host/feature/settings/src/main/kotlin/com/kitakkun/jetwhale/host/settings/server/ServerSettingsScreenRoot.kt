package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import soil.query.compose.rememberSubscription
import java.awt.Desktop

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
                debuggerSettings = debuggerSettings,
                sslCertificates = sslCertificates,
                mcpPermissionsSnapshot = mcpPermissionsSnapshot,
            )
        }

        ServerSettingsScreen(
            page = page,
            uiState = uiState,
            onDebugPortTextChange = {
                screenChannel.send(ServerSettingsScreenAction.ChangeDebugPortText(it))
            },
            onApplyDebugPortChange = {
                screenChannel.send(ServerSettingsScreenAction.ApplyDebugPortChange)
            },
            onConfirmApplyDebugPortChange = {
                screenChannel.send(ServerSettingsScreenAction.ConfirmApplyDebugPortChange)
            },
            onDismissApplyDebugPortDialog = {
                screenChannel.send(ServerSettingsScreenAction.DismissApplyDebugPortDialog)
            },
            onMcpPortTextChange = {
                screenChannel.send(ServerSettingsScreenAction.ChangeMcpPortText(it))
            },
            onApplyMcpPortChange = {
                screenChannel.send(ServerSettingsScreenAction.ApplyMcpPortChange)
            },
            onConfirmApplyMcpPortChange = {
                screenChannel.send(ServerSettingsScreenAction.ConfirmApplyMcpPortChange)
            },
            onDismissApplyMcpPortDialog = {
                screenChannel.send(ServerSettingsScreenAction.DismissApplyMcpPortDialog)
            },
            onClickOpenMcpGuide = {
                try {
                    Desktop.getDesktop().browse(java.net.URI(MCP_GUIDE_URL))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onSetHostGroupAllowed = { group, allowed ->
                screenChannel.send(ServerSettingsScreenAction.SetHostGroupAllowed(group, allowed))
            },
            onSetPluginInspectAllowed = { pluginId, allowed ->
                screenChannel.send(ServerSettingsScreenAction.SetPluginInspectAllowed(pluginId, allowed))
            },
            onSetPluginInteractAllowed = { pluginId, allowed ->
                screenChannel.send(ServerSettingsScreenAction.SetPluginInteractAllowed(pluginId, allowed))
            },
            onSetPluginToolAllowed = { toolName, allowed ->
                screenChannel.send(ServerSettingsScreenAction.SetPluginToolAllowed(toolName, allowed))
            },
            onAddCertificate = {
                screenChannel.send(ServerSettingsScreenAction.AddCertificate)
            },
            onSetActiveCertificate = {
                screenChannel.send(ServerSettingsScreenAction.SetActiveCertificate(it))
            },
            onDeleteCertificate = {
                screenChannel.send(ServerSettingsScreenAction.DeleteCertificate(it))
            },
            onShowCertificateDetail = {
                screenChannel.send(ServerSettingsScreenAction.ShowCertificateDetail(it))
            },
            onDismissCertificateDetailDialog = {
                screenChannel.send(ServerSettingsScreenAction.DismissCertificateDetailDialog)
            },
        )
    }
}
