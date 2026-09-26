package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import kotlinx.serialization.Serializable

@Serializable
data object EmptyPluginNavKey : NavKey

@Serializable
data class SettingsNavKey(
    val initialPage: SettingsScreenPage = SettingsScreenPage.Appearance,
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

/**
 * A plugin the drawer lists greyed out, opened to say why it can't run: switched off, with a way to
 * switch it on, or [notInApp] — the selected app doesn't include it, which nothing here can change.
 * [sessionId] is where the plugin opens once enabled.
 */
@Serializable
data class DisabledPluginNavKey(
    val pluginId: String,
    val pluginName: String,
    val sessionId: String?,
    val notInApp: Boolean,
) : NavKey

@Serializable
data object LogViewerNavKey : NavKey

/**
 * The MCP tools browser. [pluginId] and [sessionId] seed the screen's filters — null means
 * "all", so opening it from a plugin's badge lands on that plugin while the screen itself can
 * widen the view afterwards.
 */
@Serializable
data class McpToolsNavKey(
    val pluginId: String?,
    val sessionId: String?,
) : NavKey
