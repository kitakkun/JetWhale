package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.model.HostContent
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import kotlin.test.Test
import kotlin.test.assertEquals

class HostNavigationMappingTest {
    private val plugin = PluginNavKey(pluginId = "plugin-1", sessionId = "session-1")

    @Test
    fun `a dialog on top is the destination, and the plugin under it is the content`() {
        val destination = listOf<NavKey>(EmptyPluginNavKey, plugin, SettingsNavKey()).toHostDestination()

        assertEquals(HostDestinationKind.SETTINGS, destination.kind)
        assertEquals(HostContent(HostDestinationKind.PLUGIN, "plugin-1", "session-1"), destination.content)
    }

    @Test
    fun `with nothing over it the plugin is both`() {
        val destination = listOf<NavKey>(EmptyPluginNavKey, plugin).toHostDestination()

        assertEquals(HostDestinationKind.PLUGIN, destination.kind)
        assertEquals(HostContent(HostDestinationKind.PLUGIN, "plugin-1", "session-1"), destination.content)
    }

    @Test
    fun `a popout is neither the destination nor the content`() {
        val popout = PluginPopoutNavKey(pluginId = "plugin-2", sessionId = "session-1", pluginName = "Two")
        val destination = listOf<NavKey>(EmptyPluginNavKey, plugin, popout).toHostDestination()

        assertEquals(HostDestinationKind.PLUGIN, destination.kind)
        assertEquals("plugin-1", destination.pluginId)
        assertEquals(HostContent(HostDestinationKind.PLUGIN, "plugin-1", "session-1"), destination.content)
        assertEquals(listOf("plugin-2"), destination.poppedOutPlugins.map { it.pluginId })
    }

    @Test
    fun `an empty stack under a dialog is home`() {
        val destination = listOf<NavKey>(EmptyPluginNavKey, InfoNavKey).toHostDestination()

        assertEquals(HostDestinationKind.INFO, destination.kind)
        assertEquals(HostContent(HostDestinationKind.HOME), destination.content)
    }
}
