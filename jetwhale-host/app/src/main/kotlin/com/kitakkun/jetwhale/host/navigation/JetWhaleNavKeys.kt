package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object EmptyPluginNavKey : NavKey

@Serializable
data object SettingsNavKey : NavKey

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
data object DisabledPluginNavKey : NavKey
