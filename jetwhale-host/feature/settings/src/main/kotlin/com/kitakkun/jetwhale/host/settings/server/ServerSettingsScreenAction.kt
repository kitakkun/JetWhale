package com.kitakkun.jetwhale.host.settings.server

sealed interface ServerSettingsScreenAction {
    data class ChangeDebugPortText(val text: String) : ServerSettingsScreenAction
    data object ApplyDebugPortChange : ServerSettingsScreenAction
    data object ConfirmApplyDebugPortChange : ServerSettingsScreenAction
    data object DismissApplyDebugPortDialog : ServerSettingsScreenAction

    data class ChangeMcpPortText(val text: String) : ServerSettingsScreenAction
    data object ApplyMcpPortChange : ServerSettingsScreenAction
    data object ConfirmApplyMcpPortChange : ServerSettingsScreenAction
    data object DismissApplyMcpPortDialog : ServerSettingsScreenAction
}
