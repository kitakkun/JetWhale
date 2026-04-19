package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.settings.SettingsScreenSegmentedMenu
import kotlinx.serialization.Serializable

@Serializable
data object EmptyPluginNavKey : NavKey

@Serializable
data class SettingsNavKey(
    val initialMenu: SettingsScreenSegmentedMenu = SettingsScreenSegmentedMenu.General,
) : NavKey

@Serializable
data object LicensesNavKey : NavKey

@Serializable
data object InfoNavKey : NavKey

@Serializable
data class PluginNavKey(
    val pluginId: String,
    val sessionId: String,
) : NavKey

@Serializable
data class PluginPopoutNavKey(
    val pluginId: String,
    val sessionId: String,
    val pluginName: String,
) : NavKey

@Serializable
data object DisabledPluginNavKey : NavKey

@Serializable
data object LogViewerNavKey : NavKey
