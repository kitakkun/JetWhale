package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.drawer.McpToolsTab
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostMcpToolsTab
import com.kitakkun.jetwhale.host.model.HostSettingsPage
import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage

/**
 * Translates the back stack into the destination model that [com.kitakkun.jetwhale.host.model.HostNavigationService]
 * publishes. Nav keys and the settings menu are app-level types, so the mapping lives here rather
 * than leaking into `core/model`.
 *
 * The top-most entry that is not a popout wins: popouts render in their own windows and are
 * reported alongside whatever the main window shows.
 */
fun List<NavKey>.toHostDestination(): HostDestination {
    val poppedOut = filterIsInstance<PluginPopoutNavKey>().map { PoppedOutPlugin(it.pluginId, it.sessionId) }
    return when (val top = lastOrNull { it !is PluginPopoutNavKey }) {
        is PluginNavKey -> HostDestination(
            kind = HostDestinationKind.PLUGIN,
            pluginId = top.pluginId,
            sessionId = top.sessionId,
            poppedOutPlugins = poppedOut,
        )

        is SettingsNavKey -> HostDestination(
            kind = HostDestinationKind.SETTINGS,
            settingsPage = top.initialPage.toHostSettingsPage(),
            poppedOutPlugins = poppedOut,
        )

        is McpToolsNavKey -> HostDestination(
            kind = HostDestinationKind.MCP_TOOLS,
            pluginId = top.pluginId,
            sessionId = top.sessionId,
            mcpToolsTab = top.initialTab.toHostMcpToolsTab(),
            poppedOutPlugins = poppedOut,
        )

        InfoNavKey -> HostDestination(HostDestinationKind.INFO, poppedOutPlugins = poppedOut)

        LicensesNavKey -> HostDestination(HostDestinationKind.LICENSES, poppedOutPlugins = poppedOut)

        LogViewerNavKey -> HostDestination(HostDestinationKind.LOG_VIEWER, poppedOutPlugins = poppedOut)

        is DisabledPluginNavKey -> HostDestination(
            kind = HostDestinationKind.DISABLED_PLUGIN,
            pluginId = top.pluginId,
            sessionId = top.sessionId,
            poppedOutPlugins = poppedOut,
        )

        else -> HostDestination(HostDestinationKind.HOME, poppedOutPlugins = poppedOut)
    }
}

fun HostSettingsPage.toPage(): SettingsScreenPage = when (this) {
    HostSettingsPage.APPEARANCE -> SettingsScreenPage.Appearance
    HostSettingsPage.APPLICATION -> SettingsScreenPage.Application
    HostSettingsPage.DEBUG_SERVER -> SettingsScreenPage.DebugServer
    HostSettingsPage.SSL_CERTIFICATE -> SettingsScreenPage.SslCertificate
    HostSettingsPage.ADB_SUPPORT -> SettingsScreenPage.Adb
    HostSettingsPage.MCP_SERVER -> SettingsScreenPage.McpServer
    HostSettingsPage.PERMISSIONS -> SettingsScreenPage.McpPermissions
    HostSettingsPage.ACTIVITY -> SettingsScreenPage.AiActivity
    HostSettingsPage.INSTALLED_PLUGINS -> SettingsScreenPage.InstalledPlugins
    HostSettingsPage.ADD_PLUGINS -> SettingsScreenPage.AddPlugins
    HostSettingsPage.SECURITY -> SettingsScreenPage.PluginSecurity
}

private fun SettingsScreenPage.toHostSettingsPage(): HostSettingsPage = when (this) {
    SettingsScreenPage.Appearance -> HostSettingsPage.APPEARANCE
    SettingsScreenPage.Application -> HostSettingsPage.APPLICATION
    SettingsScreenPage.DebugServer -> HostSettingsPage.DEBUG_SERVER
    SettingsScreenPage.SslCertificate -> HostSettingsPage.SSL_CERTIFICATE
    SettingsScreenPage.Adb -> HostSettingsPage.ADB_SUPPORT
    SettingsScreenPage.McpServer -> HostSettingsPage.MCP_SERVER
    SettingsScreenPage.McpPermissions -> HostSettingsPage.PERMISSIONS
    SettingsScreenPage.AiActivity -> HostSettingsPage.ACTIVITY
    SettingsScreenPage.InstalledPlugins -> HostSettingsPage.INSTALLED_PLUGINS
    SettingsScreenPage.AddPlugins -> HostSettingsPage.ADD_PLUGINS
    SettingsScreenPage.PluginSecurity -> HostSettingsPage.SECURITY
}

fun HostMcpToolsTab.toMcpToolsTab(): McpToolsTab = when (this) {
    HostMcpToolsTab.TOOLS -> McpToolsTab.Tools
    HostMcpToolsTab.HISTORY -> McpToolsTab.History
}

private fun McpToolsTab.toHostMcpToolsTab(): HostMcpToolsTab = when (this) {
    McpToolsTab.Tools -> HostMcpToolsTab.TOOLS
    McpToolsTab.History -> HostMcpToolsTab.HISTORY
}
