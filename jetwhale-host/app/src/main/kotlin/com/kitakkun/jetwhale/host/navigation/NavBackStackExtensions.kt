package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.model.HostSession

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
 * Makes the plugin screen currently on top of the back stack follow a session switch.
 *
 * If the top entry is a [PluginNavKey] targeting a different session, it is replaced with a
 * [PluginNavKey] for [newSessionId] so the same plugin is shown for the newly-selected session.
 * If the plugin is not available on the new session (per [isPluginAvailableOnNewSession]), the old
 * plugin entry is simply popped so the underlying (e.g. empty) screen is shown instead of a dead
 * plugin screen.
 *
 * No-op when the top entry is not a [PluginNavKey], already targets [newSessionId], or is a plugin
 * of [HostSession], which belongs to no app and so stays put while the user switches apps.
 */
fun NavBackStack<NavKey>.followPluginToSession(
    newSessionId: String,
    isPluginAvailableOnNewSession: (pluginId: String) -> Boolean,
) {
    val top = lastOrNull() as? PluginNavKey ?: return
    if (top.sessionId == newSessionId || HostSession.isHost(top.sessionId)) return

    removeLastOrNull()
    if (isPluginAvailableOnNewSession(top.pluginId)) {
        // Plain add (not addSingleTop): we only replace the top entry, so earlier entries for the
        // same plugin/session deeper in the back stack must be left intact.
        add(PluginNavKey(pluginId = top.pluginId, sessionId = newSessionId))
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

/**
 * Removes the screens and popouts of plugins that are no longer installed. No new instance can be
 * created for them, so a screen left open could at best keep showing an instance that is on its way
 * out, and a disabled plugin's screen would offer to enable something that is not there.
 */
fun NavBackStack<NavKey>.removeEntriesOfUninstalledPlugins(installedPluginIds: Set<String>) {
    removeAll { navKey ->
        when (navKey) {
            is PluginNavKey -> navKey.pluginId !in installedPluginIds
            is PluginPopoutNavKey -> navKey.pluginId !in installedPluginIds
            is DisabledPluginNavKey -> navKey.pluginId !in installedPluginIds
            else -> false
        }
    }
}
