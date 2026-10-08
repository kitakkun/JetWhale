package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.drawer.McpToolsTab
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostMcpToolsTab
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

    @Test
    fun `opening the tools browser from under Settings brings it to the top as the only dialog`() {
        val mcpToolsNavKey = McpToolsNavKey(pluginId = null, sessionId = null, initialTab = McpToolsTab.Tools)
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, mcpToolsNavKey, SettingsNavKey(initialPage = SettingsScreenPage.Appearance))

        backStack.openMcpTools(pluginId = null, sessionId = null, tab = McpToolsTab.Tools)

        assertEquals(listOf(EmptyPluginNavKey, mcpToolsNavKey), backStack.toList())
        val reportedDestination = backStack.toHostDestination()
        assertEquals(HostDestinationKind.MCP_TOOLS, reportedDestination.kind)
        assertEquals(HostMcpToolsTab.TOOLS, reportedDestination.mcpToolsTab)
    }

    @Test
    fun `opening Settings closes an open tools browser`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, McpToolsNavKey(pluginId = null, sessionId = null, initialTab = McpToolsTab.Tools))

        backStack.openSettings(SettingsScreenPage.McpServer)

        assertEquals(listOf(EmptyPluginNavKey, SettingsNavKey(initialPage = SettingsScreenPage.McpServer)), backStack.toList())
    }

    @Test
    fun `opening Info closes an open Settings`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, SettingsNavKey(initialPage = SettingsScreenPage.Appearance))

        backStack.openInfo()

        assertEquals(listOf(EmptyPluginNavKey, InfoNavKey), backStack.toList())
    }
}
