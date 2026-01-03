package com.kitakkun.jetwhale.host.settings.general

import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId
import kotlinx.collections.immutable.ImmutableList

data class GeneralSettingsScreenUiState(
    val automaticallyWireADBTransport: Boolean,
    val language: AppLanguage,
    val selectedColorSchemeId: JetWhaleColorSchemeId,
    val availableColorSchemes: ImmutableList<JetWhaleColorSchemeId>,
    val appDataPath: String,
    val adbPath: String,
)
