package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import kotlinx.serialization.Serializable

/**
 * A key whose entry is drawn over the main window's content — a dialog or a window of its own —
 * rather than as that content. Navigating the content underneath must leave these where they are:
 * see [showBelowOverlays].
 */
sealed interface OverlayNavKey : NavKey

@Serializable
data object EmptyPluginNavKey : NavKey

@Serializable
data class SettingsNavKey(
    val initialPage: SettingsScreenPage = SettingsScreenPage.Appearance,
) : OverlayNavKey

@Serializable
data object LicensesNavKey : OverlayNavKey

@Serializable
data object InfoNavKey : OverlayNavKey

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
) : OverlayNavKey

@Serializable
data object DisabledPluginNavKey : NavKey

@Serializable
data object LogViewerNavKey : OverlayNavKey

/**
 * The MCP tools browser. [pluginId] and [sessionId] seed the screen's filters — null means
 * "all", so opening it from a plugin's badge lands on that plugin while the screen itself can
 * widen the view afterwards.
 */
@Serializable
data class McpToolsNavKey(
    val pluginId: String?,
    val sessionId: String?,
) : OverlayNavKey
