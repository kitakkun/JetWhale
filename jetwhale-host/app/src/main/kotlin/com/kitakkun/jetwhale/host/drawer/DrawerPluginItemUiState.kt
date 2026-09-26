package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginIconResource

/**
 * @property underAiControl True while an AI agent is driving this plugin's UI in the selected session.
 * @property exposesMcpTools True when this plugin publishes MCP tools of its own for the selected session.
 * @property isHeadless True when this plugin renders no UI in the selected session, so opening it shows nothing.
 * @property needsApp False for a plugin that runs without any app (`requiresAgent = false`): it lives in
 *   [com.kitakkun.jetwhale.host.model.HostSession], is listed apart from the selected app's plugins,
 *   and is usable with no app connected.
 * @property failureMessage The last exception this plugin's instance let escape in the selected session, kept until the instance is disposed.
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
    val needsApp: Boolean,
    val failureMessage: String?,
)
