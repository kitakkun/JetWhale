package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.SettingsScreenSection

/**
 * Translates the back stack into the destination model that [com.kitakkun.jetwhale.host.model.HostNavigationService]
 * publishes. Nav keys and the settings menu are app-level types, so the mapping lives here rather
 * than leaking into `core/model`.
 *
 * The top-most entry the main window shows wins. Popouts and the log viewer render in windows of
 * their own and are reported alongside it.
 */
fun List<NavKey>.toHostDestination(): HostDestination {
    val poppedOut = filterIsInstance<PluginPopoutNavKey>().map { PoppedOutPlugin(it.pluginId, it.sessionId) }
    val logViewerOpen = LogViewerNavKey in this
    return when (val top = lastOrNull(NavKey::showsInMainWindow)) {
        is PluginNavKey -> HostDestination(
            kind = HostDestinationKind.PLUGIN,
            pluginId = top.pluginId,
            sessionId = top.sessionId,
            poppedOutPlugins = poppedOut,
            logViewerOpen = logViewerOpen,
        )

        is SettingsNavKey -> HostDestination(
            kind = HostDestinationKind.SETTINGS,
            settingsSection = top.initialPage.toHostSettingsSection(),
            poppedOutPlugins = poppedOut,
            logViewerOpen = logViewerOpen,
        )

        is McpToolsNavKey -> HostDestination(
            kind = HostDestinationKind.MCP_TOOLS,
            pluginId = top.pluginId,
            sessionId = top.sessionId,
            poppedOutPlugins = poppedOut,
            logViewerOpen = logViewerOpen,
        )

        InfoNavKey -> HostDestination(HostDestinationKind.INFO, poppedOutPlugins = poppedOut, logViewerOpen = logViewerOpen)

        LicensesNavKey -> HostDestination(HostDestinationKind.LICENSES, poppedOutPlugins = poppedOut, logViewerOpen = logViewerOpen)

        is DisabledPluginNavKey -> HostDestination(
            kind = HostDestinationKind.DISABLED_PLUGIN,
            pluginId = top.pluginId,
            sessionId = top.sessionId,
            poppedOutPlugins = poppedOut,
            logViewerOpen = logViewerOpen,
        )

        else -> HostDestination(HostDestinationKind.HOME, poppedOutPlugins = poppedOut, logViewerOpen = logViewerOpen)
    }
}

fun HostSettingsSection.toPage(): SettingsScreenPage = when (this) {
    HostSettingsSection.GENERAL -> SettingsScreenSection.General.firstPage
    HostSettingsSection.SERVER -> SettingsScreenSection.Connection.firstPage
    HostSettingsSection.AI_AGENTS -> SettingsScreenSection.AiAgents.firstPage
    HostSettingsSection.PLUGINS -> SettingsScreenSection.Plugins.firstPage
}

private fun SettingsScreenPage.toHostSettingsSection(): HostSettingsSection = when (section) {
    SettingsScreenSection.General -> HostSettingsSection.GENERAL
    SettingsScreenSection.Connection -> HostSettingsSection.SERVER
    SettingsScreenSection.AiAgents -> HostSettingsSection.AI_AGENTS
    SettingsScreenSection.Plugins -> HostSettingsSection.PLUGINS
}
