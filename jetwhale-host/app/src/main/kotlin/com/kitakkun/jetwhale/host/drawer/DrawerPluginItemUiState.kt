package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginIconResource

/**
 * @property underAiControl True while an AI agent is driving this plugin's UI in the selected session.
 * @property exposesMcpTools True when this plugin publishes MCP tools of its own for the selected session.
 * @property isHeadless True when this plugin renders no UI in the selected session, so opening it shows nothing.
 */
data class DrawerPluginItemUiState(
    val name: String,
    val id: String,
    val activeIconResource: PluginIconResource?,
    val inactiveIconResource: PluginIconResource?,
    val pluginAvailability: PluginAvailability,
    val underAiControl: Boolean,
    val exposesMcpTools: Boolean,
    val isHeadless: Boolean,
)
