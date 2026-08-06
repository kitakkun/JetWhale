package com.kitakkun.jetwhale.host.shell

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The nav keys for screens the app module itself owns.
 *
 * They live here rather than next to their screens because the navigator implementations in this
 * module have to construct them, and this module is the lowest one that every screen's navigation
 * is visible from.
 */
@Serializable
data object EmptyPluginNavKey : NavKey

@Serializable
data object InfoNavKey : NavKey

@Serializable
data object DisabledPluginNavKey : NavKey

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
