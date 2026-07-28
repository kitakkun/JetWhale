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
    /** True when the plugin publishes its own MCP tools for the selected session. */
    val exposesMcpTools: Boolean,
)
