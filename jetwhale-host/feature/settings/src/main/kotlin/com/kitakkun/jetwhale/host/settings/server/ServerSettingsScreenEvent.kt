package com.kitakkun.jetwhale.host.settings.server

sealed interface ServerSettingsScreenEvent {
    data class ChangePortText(val text: String) : ServerSettingsScreenEvent
    data object ApplyPortChange : ServerSettingsScreenEvent
    data object ConfirmApplyPortChange : ServerSettingsScreenEvent
    data object DismissApplyPortDialog : ServerSettingsScreenEvent
}
