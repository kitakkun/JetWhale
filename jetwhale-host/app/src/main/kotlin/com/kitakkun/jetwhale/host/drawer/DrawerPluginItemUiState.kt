package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.McpCallRecord
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginIconResource
import kotlinx.collections.immutable.ImmutableList

data class DrawerPluginItemUiState(
    val name: String,
    val id: String,
    val activeIconResource: PluginIconResource?,
    val inactiveIconResource: PluginIconResource?,
    val pluginAvailability: PluginAvailability,
    /** True while an AI agent is driving this plugin's UI in the selected session. */
    val underAiControl: Boolean,
    /** The MCP tools this plugin exposes for the selected session; empty when it publishes none. */
    val mcpTools: ImmutableList<McpToolSummary>,
    /** Completed MCP tool calls attributed to this plugin, newest first. */
    val mcpCallHistory: ImmutableList<McpCallRecord>,
) {
    val exposesMcpTools: Boolean get() = mcpTools.isNotEmpty()
}
