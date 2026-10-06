package com.kitakkun.jetwhale.host.navigation

import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostSettingsSection
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
            HostDestination(kind = HostDestinationKind.SETTINGS, settingsSection = HostSettingsSection.GENERAL, logViewerOpen = true),
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
}
