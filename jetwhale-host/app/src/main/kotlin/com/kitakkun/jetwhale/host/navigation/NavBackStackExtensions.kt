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
 * Makes the plugin screen currently on top of the back stack follow a session switch.
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
    if (top.sessionId == newSessionId) return

    removeLastOrNull()
    if (isPluginAvailableOnNewSession(top.pluginId)) {
        addSingleTop(PluginNavKey(pluginId = top.pluginId, sessionId = newSessionId))
    }
}
