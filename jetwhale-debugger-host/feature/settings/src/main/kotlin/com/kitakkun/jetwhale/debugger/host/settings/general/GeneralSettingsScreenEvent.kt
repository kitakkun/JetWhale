package com.kitakkun.jetwhale.debugger.host.settings.general

import com.kitakkun.jetwhale.debugger.host.model.AppLanguage
import com.kitakkun.jetwhale.debugger.host.model.JetWhaleColorSchemeId

sealed interface GeneralSettingsScreenEvent {
    data class ChangePersistData(val shouldPersist: Boolean) : GeneralSettingsScreenEvent
    data class ChangeAutomaticallyWireADBTransport(val shouldAutomaticallyWire: Boolean) : GeneralSettingsScreenEvent
    data class AppLanguageSelected(val language: AppLanguage) : GeneralSettingsScreenEvent
    data class ColorSchemeSelected(val colorSchemeId: JetWhaleColorSchemeId) : GeneralSettingsScreenEvent
}