package com.kitakkun.jetwhale.host.plugin

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class PluginNavKey(
    val pluginId: String,
    val sessionId: String,
) : NavKey

@Serializable
data class PluginPopoutNavKey(
    val pluginId: String,
    val sessionId: String,
    val pluginName: String,
) : NavKey

/**
 * Everything a plugin screen — and the shell that hosts it — can ask the host to navigate to.
 *
 * The popout state is readable here because a plugin entry has to know whether its own content is
 * being shown in a separate window instead of the main one.
 */
interface PluginNavigator {
    /** Plugins currently shown in their own popout windows. */
    val poppedOutPlugins: StateFlow<List<PoppedOutPlugin>>

    fun openPlugin(pluginId: String, sessionId: String)

    /** Moves the plugin into a window of its own; the main window shows a placeholder instead. */
    fun popOut(pluginId: String, pluginName: String, sessionId: String)

    /** Docks a popped-out plugin: shows it in the main window and closes its popout window. */
    fun bringBackToMainWindow(pluginId: String, sessionId: String)

    fun closeAllPluginScreens()

    fun closePluginScreensForSession(sessionId: String)

    fun closePluginScreensForPlugin(pluginId: String)

    /**
     * Makes the plugin screen currently on top follow a session switch, so the same plugin is shown
     * for [newSessionId]. A plugin missing from [availablePluginIds] is closed instead, so the
     * screen underneath is shown rather than a dead plugin screen.
     */
    fun followPluginToSession(newSessionId: String, availablePluginIds: Set<String>)
}

/** Whether [pluginId] is currently shown in a separate popout window for [sessionId]. */
fun List<PoppedOutPlugin>.isPoppedOut(pluginId: String, sessionId: String): Boolean = any {
    it.pluginId == pluginId && it.sessionId == sessionId
}
