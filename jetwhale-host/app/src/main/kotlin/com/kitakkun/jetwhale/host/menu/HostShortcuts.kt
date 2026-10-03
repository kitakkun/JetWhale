package com.kitakkun.jetwhale.host.menu

import androidx.compose.ui.input.key.Key

/**
 * Every keyboard shortcut the host binds. None takes a key that text editing uses or that a bundled
 * plugin binds for itself (Debug Actions' K), so a focused text field or plugin keeps its keys.
 */
internal object HostShortcuts {
    val settings = HostShortcut(Key.Comma, withShift = false)
    val quit = HostShortcut(Key.Q, withShift = false)
    val home = HostShortcut(Key.H, withShift = true)
    val logViewer = HostShortcut(Key.L, withShift = true)
    val closeWindow = HostShortcut(Key.W, withShift = false)

    private val pluginKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

    /** The shortcut of the plugin at [position] in the Plugins menu; only the first nine get one. */
    fun plugin(position: Int): HostShortcut? = pluginKeys.getOrNull(position)?.let { HostShortcut(it, withShift = false) }
}
