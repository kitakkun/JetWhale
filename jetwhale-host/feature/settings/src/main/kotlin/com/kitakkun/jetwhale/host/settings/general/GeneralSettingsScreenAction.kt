package com.kitakkun.jetwhale.host.settings.general

import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId

sealed interface GeneralSettingsScreenAction {
    data class ChangeAutomaticallyWireADBTransport(val shouldAutomaticallyWire: Boolean) : GeneralSettingsScreenAction

    data class AppLanguageSelected(val language: AppLanguage) : GeneralSettingsScreenAction

    data class ColorSchemeSelected(val colorSchemeId: JetWhaleColorSchemeId) : GeneralSettingsScreenAction

    data class ChangeFollowAiOperation(val enabled: Boolean) : GeneralSettingsScreenAction

    data class ChangeCheckForUpdatesOnStartup(val enabled: Boolean) : GeneralSettingsScreenAction

    data object CheckForUpdates : GeneralSettingsScreenAction

    data object DownloadUpdate : GeneralSettingsScreenAction

    data object CancelUpdateDownload : GeneralSettingsScreenAction

    data object RestartToUpdate : GeneralSettingsScreenAction

    data class TryHostVersionAgain(val version: String) : GeneralSettingsScreenAction
}
