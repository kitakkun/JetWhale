package com.kitakkun.jetwhale.host.navigation

import com.kitakkun.jetwhale.host.drawer.McpToolsTab
import com.kitakkun.jetwhale.host.model.HostSettingsPage
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.SettingsScreenSection
import kotlin.test.Test
import kotlin.test.assertEquals

class HostNavigationMappingTest {

    @Test
    fun `every settings page an agent requests opens that page and is reported back`() {
        HostSettingsPage.entries.forEach { page ->
            val reported = listOf(EmptyPluginNavKey, SettingsNavKey(initialPage = page.toPage())).toHostDestination()

            assertEquals(page, reported.settingsPage)
        }
    }

    @Test
    fun `the host's settings pages are the menu's pages in the menu's order`() {
        val reported = SettingsScreenPage.entries.map { listOf(SettingsNavKey(initialPage = it)).toHostDestination().settingsPage }

        assertEquals(HostSettingsPage.entries, reported)
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
            val reported = listOf(SettingsNavKey(initialPage = page)).toHostDestination()

            assertEquals(hostSectionByMenuSection.getValue(page.section), reported.settingsPage?.section, page.name)
        }
    }

    @Test
    fun `every tools browser tab an agent requests opens that tab and is reported back`() {
        McpToolsTab.entries.forEach { tab ->
            val reported = listOf(McpToolsNavKey(pluginId = null, sessionId = null, initialTab = tab)).toHostDestination()

            assertEquals(tab, reported.mcpToolsTab?.toMcpToolsTab())
        }
    }
}
