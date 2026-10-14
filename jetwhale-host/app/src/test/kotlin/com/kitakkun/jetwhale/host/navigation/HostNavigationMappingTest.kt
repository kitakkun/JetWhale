package com.kitakkun.jetwhale.host.navigation

import com.kitakkun.jetwhale.host.drawer.McpToolsTab
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostSettingsPage
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.SettingsScreenSection
import kotlin.test.Test
import kotlin.test.assertEquals

class HostNavigationMappingTest {
    private val pluginNavKey = PluginNavKey(pluginId = "com.example.network", sessionId = "app-1")

    @Test
    fun `the home screen under the log viewer is reported as home with the log viewer open`() {
        assertEquals(
            HostDestination(kind = HostDestinationKind.HOME, logViewerOpen = true),
            listOf(EmptyPluginNavKey, LogViewerNavKey).toHostDestination(),
        )
    }

    @Test
    fun `settings opened before the log viewer is what the main window reports`() {
        assertEquals(
            HostDestination(kind = HostDestinationKind.SETTINGS, settingsPage = HostSettingsPage.APPEARANCE, logViewerOpen = true),
            listOf(EmptyPluginNavKey, SettingsNavKey(), LogViewerNavKey).toHostDestination(),
        )
    }

    @Test
    fun `a plugin under the log viewer is reported as on screen in the main window`() {
        assertEquals(
            HostDestination(kind = HostDestinationKind.PLUGIN, pluginId = pluginNavKey.pluginId, sessionId = pluginNavKey.sessionId, logViewerOpen = true),
            listOf(EmptyPluginNavKey, pluginNavKey, LogViewerNavKey).toHostDestination(),
        )
    }

    @Test
    fun `a closed log viewer is reported closed`() {
        assertEquals(
            HostDestination(kind = HostDestinationKind.PLUGIN, pluginId = pluginNavKey.pluginId, sessionId = pluginNavKey.sessionId, logViewerOpen = false),
            listOf(EmptyPluginNavKey, pluginNavKey).toHostDestination(),
        )
    }

    @Test
    fun `every settings page an agent requests opens that page and is reported back`() {
        HostSettingsPage.entries.forEach { page ->
            val reportedDestination = listOf(EmptyPluginNavKey, SettingsNavKey(initialPage = page.toPage())).toHostDestination()

            assertEquals(page, reportedDestination.settingsPage)
        }
    }

    @Test
    fun `the host's settings pages are the menu's pages in the menu's order`() {
        val reportedSettingsPages = SettingsScreenPage.entries.map { listOf(SettingsNavKey(initialPage = it)).toHostDestination().settingsPage }

        assertEquals(HostSettingsPage.entries, reportedSettingsPages)
    }

    @Test
    fun `each settings page is in the section the menu files it under`() {
        val hostSectionByMenuSection = mapOf(
            SettingsScreenSection.General to HostSettingsSection.GENERAL,
            SettingsScreenSection.Connection to HostSettingsSection.SERVER,
            SettingsScreenSection.AiAgents to HostSettingsSection.AI_AGENTS,
            SettingsScreenSection.Plugins to HostSettingsSection.PLUGINS,
        )
        SettingsScreenPage.entries.forEach { page ->
            val reportedDestination = listOf(SettingsNavKey(initialPage = page)).toHostDestination()

            assertEquals(hostSectionByMenuSection.getValue(page.section), reportedDestination.settingsPage?.section, page.name)
        }
    }

    @Test
    fun `every tools browser tab an agent requests opens that tab and is reported back`() {
        McpToolsTab.entries.forEach { tab ->
            val reportedDestination = listOf(McpToolsNavKey(pluginId = null, sessionId = null, initialTab = tab)).toHostDestination()

            assertEquals(tab, reportedDestination.mcpToolsTab?.toMcpToolsTab())
        }
    }
}
