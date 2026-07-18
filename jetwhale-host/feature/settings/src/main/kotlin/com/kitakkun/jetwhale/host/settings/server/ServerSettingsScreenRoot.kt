package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.SoilDataBoundary
import com.kitakkun.jetwhale.host.architecture.rememberScreenChannel
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext
import soil.query.compose.rememberSubscription

@Composable
context(screenContext: SettingsScreenContext)
fun ServerSettingsScreenRoot() {
    SoilDataBoundary(
        state1 = rememberSubscription(screenContext.serverStatusSubscriptionKey),
        state2 = rememberSubscription(screenContext.mcpServerStatusSubscriptionKey),
        state3 = rememberSubscription(screenContext.settingsSubscriptionKey),
        state4 = rememberSubscription(screenContext.sslCertificatesSubscriptionKey),
    ) { serverStatus, mcpServerStatus, debuggerSettings, sslCertificates ->
        val screenChannel = rememberScreenChannel<ServerSettingsScreenAction, Nothing>()
        val uiState = context(screenContext.presenterContext) {
            serverSettingsScreenPresenter(
                screenChannel = screenChannel,
                serverStatus = serverStatus,
                mcpServerStatus = mcpServerStatus,
                debuggerSettings = debuggerSettings,
                sslCertificates = sslCertificates,
            )
        }

        ServerSettingsScreen(
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
            onRestartServer = {
                screenChannel.send(ServerSettingsScreenAction.RestartServer)
            },
            onDismissRestartRequiredDialog = {
                screenChannel.send(ServerSettingsScreenAction.DismissRestartRequiredDialog)
            },
        )
    }
}
