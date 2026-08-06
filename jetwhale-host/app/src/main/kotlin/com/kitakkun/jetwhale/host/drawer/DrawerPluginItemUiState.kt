package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginIconResource

data class DrawerPluginItemUiState(
    val name: String,
    val id: String,
    val activeIconResource: PluginIconResource?,
    val inactiveIconResource: PluginIconResource?,
    val pluginAvailability: PluginAvailability,
    /** True while an AI agent is driving this plugin's UI in the selected session. */
    val underAiControl: Boolean,
    /** True when this plugin publishes MCP tools of its own for the selected session. */
    val exposesMcpTools: Boolean,
    /** True when this plugin renders no UI in the selected session, so opening it shows nothing. */
    val isHeadless: Boolean,
)
