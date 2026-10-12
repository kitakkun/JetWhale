package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginIconResource

/**
 * One row of the drawer: a plugin's instance in one session. A plugin that runs in the host session
 * as well as in apps has two rows, one in each section, told apart by [sessionId].
 *
 * @property sessionId The session whose instance this row opens: [HostSession.ID] for a row in the
 *   host section, which is usable with no app connected; otherwise the selected app, or null while no
 *   app is selected.
 * @property isSelected True for the row the user last opened, matched by plugin and session.
 * @property underAiControl True while an AI agent is driving this row's instance.
 * @property exposesMcpTools True when this row's instance publishes MCP tools of its own.
 * @property isHeadless True when this row's instance renders no UI, so opening it shows nothing.
 */
data class DrawerPluginItemUiState(
    val name: String,
    val id: String,
    val sessionId: String?,
    val activeIconResource: PluginIconResource?,
    val inactiveIconResource: PluginIconResource?,
    val pluginAvailability: PluginAvailability,
    val isSelected: Boolean,
    val underAiControl: Boolean,
    val exposesMcpTools: Boolean,
    val isHeadless: Boolean,
) {
    /** False for a row in the host section, which is listed apart from the selected app's plugins. */
    val needsApp: Boolean get() = !HostSession.isHost(sessionId)

    /** Tells this row from the other row of the same plugin, for a list that shows both. */
    val rowKey: String get() = "$id@$sessionId"
}
