package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.drawer.McpToolsTab
import com.kitakkun.jetwhale.host.model.HostContent
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostSettingsPage
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.SettingsScreenSection
import kotlin.test.Test
import kotlin.test.assertEquals

class HostNavigationMappingTest {
    private val plugin = PluginNavKey(pluginId = "plugin-1", sessionId = "session-1")

    @Test
    fun `a dialog on top is the destination and the plugin under it is the content`() {
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
        assertEquals(listOf("plugin-2"), destination.poppedOutPlugins.map(PoppedOutPlugin::pluginId))
    }

    @Test
    fun `an empty stack under a dialog is home`() {
        val destination = listOf<NavKey>(EmptyPluginNavKey, InfoNavKey).toHostDestination()

        assertEquals(HostDestinationKind.INFO, destination.kind)
        assertEquals(HostContent(HostDestinationKind.HOME), destination.content)
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
