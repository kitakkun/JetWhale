package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.model.HostSession
import kotlin.test.Test
import kotlin.test.assertEquals

class UninstalledPluginBackStackTest {
    @Test
    fun `screens and popouts of a plugin that is no longer installed are closed`() {
        val removed = PluginNavKey(pluginId = "com.example.removed", sessionId = HostSession.ID)
        val removedPopout = PluginPopoutNavKey(pluginId = "com.example.removed", sessionId = "app-1", pluginName = "Removed")
        val kept = PluginNavKey(pluginId = "com.example.kept", sessionId = "app-1")
        val disabled = DisabledPluginNavKey(pluginId = "com.example.removed", pluginName = "Removed", sessionId = "app-1", notInApp = false)
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, removed, kept, removedPopout, disabled)

        backStack.removeEntriesOfUninstalledPlugins(installedPluginIds = setOf("com.example.kept"))

        assertEquals(listOf(EmptyPluginNavKey, kept, disabled), backStack.toList())
    }
}
