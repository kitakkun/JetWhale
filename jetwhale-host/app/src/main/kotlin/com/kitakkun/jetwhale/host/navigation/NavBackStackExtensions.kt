package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.model.HostSession

fun <T : NavKey> NavBackStack<T>.addSingleTop(navKey: T) {
    removeIf { it == navKey }
    add(navKey)
}

/**
 * Goes back in the main window: removes the top entry it shows, leaving the windows of their own
 * (see [showsInMainWindow]), so dismissing a dialog opened before or after them closes the dialog.
 * The home screen at the bottom is never removed.
 */
fun NavBackStack<NavKey>.popMainWindowEntry() {
    val top = indexOfLastPoppable()
    if (top >= 0) removeAt(top)
}

/** Whether [popMainWindowEntry] has anything to remove. */
fun NavBackStack<NavKey>.canPopMainWindowEntry(): Boolean = indexOfLastPoppable() >= 0

private fun List<NavKey>.indexOfLastPoppable(): Int = indexOfLast { it.showsInMainWindow && it !is EmptyPluginNavKey }

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
 * Whether the given plugin is currently shown in a separate popout window for [sessionId].
 */
fun NavBackStack<NavKey>.isPluginPoppedOut(pluginId: String, sessionId: String): Boolean = any {
    it is PluginPopoutNavKey &&
        it.pluginId == pluginId &&
        it.sessionId == sessionId
}

/**
 * Docks a popped-out plugin: shows it in the main window and closes its popout window.
 */
fun NavBackStack<NavKey>.bringPluginBackToMainWindow(pluginId: String, sessionId: String) {
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
 * Removes every plugin screen and popout that belongs to an app, for when the server stops and takes
 * every app with it. Those of [HostSession] stay: they need no app and are still running.
 */
fun NavBackStack<NavKey>.removeAppPluginEntries() {
    removeAll { navKey ->
        when (navKey) {
            is PluginNavKey -> !HostSession.isHost(navKey.sessionId)
            is PluginPopoutNavKey -> !HostSession.isHost(navKey.sessionId)
            is DisabledPluginNavKey -> !HostSession.isHost(navKey.sessionId)
            else -> false
        }
    }
}

/**
 * Makes the plugin screen the main window shows follow a session switch.
 *
 * If the main window's top entry is a [PluginNavKey] targeting a different session, it is replaced
 * in place with a [PluginNavKey] for [newSessionId] so the same plugin is shown for the
 * newly-selected session. If the plugin is not available on the new session (per
 * [isPluginAvailableOnNewSession]), the old plugin entry is simply removed so the underlying (e.g.
 * empty) screen is shown instead of a dead plugin screen.
 *
 * No-op when the main window's top entry is not a [PluginNavKey], already targets [newSessionId],
 * or is a plugin of [HostSession], which belongs to no app and so stays put while the user switches
 * apps.
 */
fun NavBackStack<NavKey>.followPluginToSession(
    newSessionId: String,
    isPluginAvailableOnNewSession: (pluginId: String) -> Boolean,
) {
    val topIndex = indexOfLast(NavKey::showsInMainWindow)
    val top = getOrNull(topIndex) as? PluginNavKey ?: return
    if (top.sessionId == newSessionId || HostSession.isHost(top.sessionId)) return

    removeAt(topIndex)
    if (isPluginAvailableOnNewSession(top.pluginId)) {
        add(topIndex, PluginNavKey(pluginId = top.pluginId, sessionId = newSessionId))
    }
}

/**
 * Replaces the screen of a plugin that was just switched on with the plugin itself. A plugin with no
 * session to open in only leaves its screen.
 */
fun NavBackStack<NavKey>.openEnabledPlugin(navKey: DisabledPluginNavKey) {
    remove(navKey)
    navKey.sessionId?.let { addSingleTop(PluginNavKey(navKey.pluginId, it)) }
}
