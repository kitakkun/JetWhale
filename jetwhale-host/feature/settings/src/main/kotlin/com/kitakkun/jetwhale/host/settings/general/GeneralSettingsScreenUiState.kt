package com.kitakkun.jetwhale.host.settings.general

import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId
import com.kitakkun.jetwhale.host.model.UpdateCheckResult
import kotlinx.collections.immutable.ImmutableList

data class GeneralSettingsScreenUiState(
    val automaticallyWireADBTransport: Boolean,
    val language: AppLanguage,
    val selectedColorSchemeId: JetWhaleColorSchemeId,
    val availableColorSchemes: ImmutableList<JetWhaleColorSchemeId>,
    val appDataPath: String,
    val adbPath: String,
    val currentVersion: String,
    val isCheckingForUpdates: Boolean,
    val updateCheckResult: UpdateCheckResult?,
    val updateCheckError: String?,
)
