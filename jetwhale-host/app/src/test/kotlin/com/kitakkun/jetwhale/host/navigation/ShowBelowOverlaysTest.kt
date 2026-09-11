package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Content navigation — a drawer click, an agent's follow, an MCP navigate — changes what the main
 * window shows underneath and leaves every dialog and popout window where it is.
 */
class ShowBelowOverlaysTest {
    private val plugin = PluginNavKey(pluginId = "plugin-1", sessionId = "session-1")
    private val otherPlugin = PluginNavKey(pluginId = "plugin-2", sessionId = "session-1")
    private val popout = PluginPopoutNavKey(pluginId = "plugin-3", sessionId = "session-1", pluginName = "Three")

    @Test
    fun `a plugin shown under an open settings dialog leaves the dialog up`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, otherPlugin, SettingsNavKey())

        backStack.showBelowOverlays(plugin)

        assertEquals(listOf(EmptyPluginNavKey, otherPlugin, plugin, SettingsNavKey()), backStack.toList())
    }

    @Test
    fun `every overlay at the top stays above the new content`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, popout, SettingsNavKey(), LogViewerNavKey)

        backStack.showBelowOverlays(plugin)

        assertEquals(listOf(EmptyPluginNavKey, plugin, popout, SettingsNavKey(), LogViewerNavKey), backStack.toList())
    }

    @Test
    fun `with nothing over the content the key goes on top`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, otherPlugin)

        backStack.showBelowOverlays(plugin)

        assertEquals(listOf(EmptyPluginNavKey, otherPlugin, plugin), backStack.toList())
    }

    @Test
    fun `showing a plugin already in the stack moves it up to the content top`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, plugin, otherPlugin, SettingsNavKey())

        backStack.showBelowOverlays(plugin)

        assertEquals(listOf(EmptyPluginNavKey, otherPlugin, plugin, SettingsNavKey()), backStack.toList())
    }

    @Test
    fun `an overlay below the content top is content history, not something to preserve on top`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, SettingsNavKey(), otherPlugin)

        backStack.showBelowOverlays(plugin)

        assertEquals(listOf(EmptyPluginNavKey, SettingsNavKey(), otherPlugin, plugin), backStack.toList())
    }
}
