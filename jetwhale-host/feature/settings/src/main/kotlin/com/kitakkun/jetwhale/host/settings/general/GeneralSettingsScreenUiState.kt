package com.kitakkun.jetwhale.host.settings.general

import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.HostUpdateStatus
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId
import kotlinx.collections.immutable.ImmutableList

data class GeneralSettingsScreenUiState(
    val automaticallyWireADBTransport: Boolean,
    val language: AppLanguage,
    val selectedColorSchemeId: JetWhaleColorSchemeId,
    val availableColorSchemes: ImmutableList<JetWhaleColorSchemeId>,
    val appDataPath: String,
    val adbPath: String,
    val currentVersion: String,
    val followAiOperation: Boolean,
    val updates: HostUpdatesUiState,
)

/**
 * @property setAsideVersion An installed version the launcher set aside after it failed its first
 * starts, which the user can try again.
 */
data class HostUpdatesUiState(
    val status: HostUpdateStatus,
    val setAsideVersion: String?,
    val checkForUpdatesOnStartup: Boolean,
)
