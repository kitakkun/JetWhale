package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class DisabledPluginBackStackTest {
    private val disabled = DisabledPluginNavKey(
        pluginId = "com.example.network",
        pluginName = "Network",
        sessionId = "app-1",
        notInApp = false,
    )

    @Test
    fun `enabling a disabled plugin replaces its screen with the plugin`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, disabled)

        backStack.openEnabledPlugin(disabled)

        assertEquals(listOf(EmptyPluginNavKey, PluginNavKey(pluginId = "com.example.network", sessionId = "app-1")), backStack.toList())
    }

    @Test
    fun `a plugin enabled with no session to open in only leaves its screen`() {
        val noSession = disabled.copy(sessionId = null)
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, noSession)

        backStack.openEnabledPlugin(noSession)

        assertEquals(listOf<NavKey>(EmptyPluginNavKey), backStack.toList())
    }
}
