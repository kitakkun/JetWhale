package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

fun <T : NavKey> NavBackStack<T>.addSingleTop(navKey: T) {
    removeIf { it == navKey }
    add(navKey)
}

fun <T : NavKey> NavBackStack<T>.addSingleTop(index: Int, navKey: T) {
    removeIf { it == navKey }
    add(index, navKey)
}

/**
 * Shows the MCP tools browser, seeded with the scope it was opened from.
 *
 * At most one browser window exists: opening it from a different scope re-seeds the filters rather
 * than stacking a second window, and re-opening it with the same scope leaves the window as it is so
 * the user does not lose its position or their own filter changes.
 */
fun NavBackStack<NavKey>.openMcpTools(pluginId: String?, sessionId: String?) {
    val navKey = McpToolsNavKey(pluginId = pluginId, sessionId = sessionId)
    if (any { it == navKey }) return
    removeAll { it is McpToolsNavKey }
    add(navKey)
}

/**
 * Whether the given plugin is currently shown in a separate popout window for [sessionId]. A
 * host-scoped plugin (null [sessionId]) is never popped out: popouts are opened per session.
 */
fun NavBackStack<NavKey>.isPluginPoppedOut(pluginId: String, sessionId: String?): Boolean = any {
    it is PluginPopoutNavKey &&
        it.pluginId == pluginId &&
        it.sessionId == sessionId
}

/**
 * Docks a popped-out plugin: shows it in the main window and closes its popout window.
 */
fun NavBackStack<NavKey>.bringPluginBackToMainWindow(pluginId: String, sessionId: String?) {
    addSingleTop(
        PluginNavKey(
            pluginId = pluginId,
            sessionId = sessionId,
        ),
    )
    removeAll {
        it is PluginPopoutNavKey &&
            it.pluginId == pluginId &&
            it.sessionId == sessionId
    }
}

/**
 * Makes the plugin screen currently on top of the back stack follow a session switch.
 *
 * A host-scoped plugin's screen is left where it is: it has no session to follow.
 *
 * If the top entry is a [PluginNavKey] targeting a different session, it is replaced with a
 * [PluginNavKey] for [newSessionId] so the same plugin is shown for the newly-selected session.
 * If the plugin is not available on the new session (per [isPluginAvailableOnNewSession]), the old
 * plugin entry is simply popped so the underlying (e.g. empty) screen is shown instead of a dead
 * plugin screen.
 *
 * No-op when the top entry is not a [PluginNavKey] or already targets [newSessionId].
 */
fun NavBackStack<NavKey>.followPluginToSession(
    newSessionId: String,
    isPluginAvailableOnNewSession: (pluginId: String) -> Boolean,
) {
    val top = lastOrNull() as? PluginNavKey ?: return
    // A host-scoped plugin's screen belongs to no session, so a session switch leaves it alone.
    if (top.sessionId == null || top.sessionId == newSessionId) return

    removeLastOrNull()
    if (isPluginAvailableOnNewSession(top.pluginId)) {
        // Plain add (not addSingleTop): we only replace the top entry, so earlier entries for the
        // same plugin/session deeper in the back stack must be left intact.
        add(PluginNavKey(pluginId = top.pluginId, sessionId = newSessionId))
    }
}
