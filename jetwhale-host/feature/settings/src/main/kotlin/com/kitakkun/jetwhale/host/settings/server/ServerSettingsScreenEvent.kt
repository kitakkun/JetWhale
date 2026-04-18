package com.kitakkun.jetwhale.host.settings.server

sealed interface ServerSettingsScreenEvent {
    data class ChangeDebugPortText(val text: String) : ServerSettingsScreenEvent
    data object ApplyDebugPortChange : ServerSettingsScreenEvent
    data object ConfirmApplyDebugPortChange : ServerSettingsScreenEvent
    data object DismissApplyDebugPortDialog : ServerSettingsScreenEvent

    data class ChangeMcpPortText(val text: String) : ServerSettingsScreenEvent
    data object ApplyMcpPortChange : ServerSettingsScreenEvent
    data object ConfirmApplyMcpPortChange : ServerSettingsScreenEvent
    data object DismissApplyMcpPortDialog : ServerSettingsScreenEvent
}
