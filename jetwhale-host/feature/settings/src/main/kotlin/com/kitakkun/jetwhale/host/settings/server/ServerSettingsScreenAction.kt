package com.kitakkun.jetwhale.host.settings.server

import com.kitakkun.jetwhale.host.model.McpHostToolGroup

sealed interface ServerSettingsScreenAction {
    data class ChangeDebugPortText(val text: String) : ServerSettingsScreenAction
    data class ChangeWssPortText(val text: String) : ServerSettingsScreenAction
    data class ChangeWssEnabled(val enabled: Boolean) : ServerSettingsScreenAction
    data object ApplyDebugServerSettingsChange : ServerSettingsScreenAction
    data object ConfirmApplyDebugServerSettingsChange : ServerSettingsScreenAction
    data object DismissApplyDebugServerSettingsDialog : ServerSettingsScreenAction

    data class ChangeMcpPortText(val text: String) : ServerSettingsScreenAction
    data object ApplyMcpPortChange : ServerSettingsScreenAction
    data object ConfirmApplyMcpPortChange : ServerSettingsScreenAction
    data object DismissApplyMcpPortDialog : ServerSettingsScreenAction

    data class SetHostGroupAllowed(val group: McpHostToolGroup, val allowed: Boolean) : ServerSettingsScreenAction
    data class SetPluginInspectAllowed(val pluginId: String, val allowed: Boolean) : ServerSettingsScreenAction
    data class SetPluginInteractAllowed(val pluginId: String, val allowed: Boolean) : ServerSettingsScreenAction
    data class SetPluginToolAllowed(val toolName: String, val allowed: Boolean) : ServerSettingsScreenAction

    data object AddCertificate : ServerSettingsScreenAction
    data class SetActiveCertificate(val id: String) : ServerSettingsScreenAction
    data class DeleteCertificate(val id: String) : ServerSettingsScreenAction
    data class ShowCertificateDetail(val id: String) : ServerSettingsScreenAction
    data object DismissCertificateDetailDialog : ServerSettingsScreenAction
}
