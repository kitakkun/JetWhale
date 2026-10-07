package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.drawer.McpToolsTab
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import kotlin.test.Test
import kotlin.test.assertEquals

class DialogBackStackTest {

    @Test
    fun `opening Settings at another page replaces the open Settings instead of stacking a second`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, SettingsNavKey(initialPage = SettingsScreenPage.Appearance))

        backStack.openSettings(SettingsScreenPage.McpPermissions)

        assertEquals(listOf(EmptyPluginNavKey, SettingsNavKey(initialPage = SettingsScreenPage.McpPermissions)), backStack.toList())
    }

    @Test
    fun `opening the tools browser on another tab replaces the open browser`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, McpToolsNavKey(pluginId = null, sessionId = null, initialTab = McpToolsTab.Tools))

        backStack.openMcpTools(pluginId = null, sessionId = null, tab = McpToolsTab.History)

        assertEquals(listOf(EmptyPluginNavKey, McpToolsNavKey(pluginId = null, sessionId = null, initialTab = McpToolsTab.History)), backStack.toList())
    }
}
