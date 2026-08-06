package com.kitakkun.jetwhale.host.shell

import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.plugin.PluginNavKey
import com.kitakkun.jetwhale.host.plugin.PluginPopoutNavKey
import com.kitakkun.jetwhale.host.settings.SettingsNavKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationBusTest {

    private val bus = NavigationBus()

    @Test
    fun `a command sent before a collector arrives is still delivered`() = runBlocking {
        bus.send(NavCommand.GoHome)

        assertEquals(NavCommand.GoHome, bus.commands.first())
    }

    @Test
    fun `each command is delivered once, in order`() = runBlocking {
        val received = mutableListOf<NavCommand>()
        val collector = launch { bus.commands.take(2).toList(received) }

        bus.send(NavCommand.GoHome)
        bus.send(NavCommand.PopTop)
        collector.join()

        assertEquals(listOf(NavCommand.GoHome, NavCommand.PopTop), received)
    }

    @Test
    fun `a plugin request sent before a collector arrives is still delivered`() = runBlocking {
        bus.requestPlugin(ExternalPluginRequest("com.example", sessionId = null))

        assertEquals(
            ExternalPluginRequest("com.example", sessionId = null),
            bus.externalPluginRequests.first(),
        )
    }

    @Test
    fun `currentView stays null until a back stack is published`() {
        bus.updateSelection(selectedSessionId = "session-1", selectedPluginId = "plugin-1")

        assertNull(bus.currentView.value)
    }

    @Test
    fun `currentView combines the published back stack with the drawer selection`() {
        bus.updateSelection(selectedSessionId = "session-1", selectedPluginId = "com.example")
        bus.publishBackStack(listOf(EmptyPluginNavKey, PluginNavKey("com.example", "session-1")))

        val view = requireNotNull(bus.currentView.value)
        assertEquals(HostDestinationKind.PLUGIN, view.destination.kind)
        assertEquals("com.example", view.destination.pluginId)
        assertEquals("session-1", view.selectedSessionId)
        assertEquals("com.example", view.selectedPluginId)
    }

    @Test
    fun `a later selection update keeps the reported destination`() {
        bus.publishBackStack(listOf(EmptyPluginNavKey, SettingsNavKey()))
        bus.updateSelection(selectedSessionId = "session-2", selectedPluginId = null)

        val view = requireNotNull(bus.currentView.value)
        assertEquals(HostDestinationKind.SETTINGS, view.destination.kind)
        assertEquals("session-2", view.selectedSessionId)
        assertNull(view.selectedPluginId)
    }

    @Test
    fun `publishing the back stack updates the popped-out plugins`() {
        bus.publishBackStack(
            listOf(
                EmptyPluginNavKey,
                PluginPopoutNavKey("com.example", "session-1", "Example"),
            ),
        )

        assertEquals(listOf("com.example"), bus.poppedOutPlugins.value.map { it.pluginId })
    }
}
