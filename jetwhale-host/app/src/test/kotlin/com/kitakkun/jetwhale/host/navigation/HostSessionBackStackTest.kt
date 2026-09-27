package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.model.HostSession
import kotlin.test.Test
import kotlin.test.assertEquals

class HostSessionBackStackTest {
    private val tool = PluginNavKey(pluginId = "com.example.device", sessionId = HostSession.ID)
    private val appPlugin = PluginNavKey(pluginId = "com.example.network", sessionId = "app-1")

    @Test
    fun `switching apps leaves a tool on screen where it is`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, appPlugin, tool)

        backStack.followPluginToSession(newSessionId = "app-2", isPluginAvailableOnNewSession = { true })

        assertEquals(listOf(EmptyPluginNavKey, appPlugin, tool), backStack.toList())
    }

    @Test
    fun `switching apps still moves an app plugin on screen to the new app`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, tool, appPlugin)

        backStack.followPluginToSession(newSessionId = "app-2", isPluginAvailableOnNewSession = { true })

        assertEquals(listOf(EmptyPluginNavKey, tool, appPlugin.copy(sessionId = "app-2")), backStack.toList())
    }

    @Test
    fun `the server stopping removes the apps' screens and keeps the tools'`() {
        val toolPopout = PluginPopoutNavKey(pluginId = "com.example.device", sessionId = HostSession.ID, pluginName = "Device")
        val appPopout = PluginPopoutNavKey(pluginId = "com.example.network", sessionId = "app-1", pluginName = "Network")
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, appPlugin, tool, appPopout, toolPopout)

        backStack.removeAppPluginEntries()

        assertEquals(listOf(EmptyPluginNavKey, tool, toolPopout), backStack.toList())
    }

    @Test
    fun `the server stopping removes an app plugin's explanation screen and keeps a host one's`() {
        val appDisabled = DisabledPluginNavKey(
            pluginId = "com.example.network",
            pluginName = "Network",
            sessionId = "app-1",
            notInApp = false,
        )
        val hostDisabled = DisabledPluginNavKey(
            pluginId = "com.example.device",
            pluginName = "Device",
            sessionId = HostSession.ID,
            notInApp = false,
        )
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, appDisabled, hostDisabled)

        backStack.removeAppPluginEntries()

        assertEquals(listOf(EmptyPluginNavKey, hostDisabled), backStack.toList())
    }
}
