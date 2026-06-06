package com.kitakkun.jetwhale.host.settings.general

import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId

sealed interface GeneralSettingsScreenAction {
    data class ChangePersistData(val shouldPersist: Boolean) : GeneralSettingsScreenAction
    data class ChangeAutomaticallyWireADBTransport(val shouldAutomaticallyWire: Boolean) : GeneralSettingsScreenAction
    data class AppLanguageSelected(val language: AppLanguage) : GeneralSettingsScreenAction
    data class ColorSchemeSelected(val colorSchemeId: JetWhaleColorSchemeId) : GeneralSettingsScreenAction
}
