package com.kitakkun.jetwhale.host.shell

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import com.kitakkun.jetwhale.host.plugin.PluginNavKey
import com.kitakkun.jetwhale.host.plugin.PluginPopoutNavKey
import com.kitakkun.jetwhale.host.settings.SettingsNavKey
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.SettingsScreenSection
import com.kitakkun.jetwhale.host.settings.licenses.LicensesNavKey
import com.kitakkun.jetwhale.host.settings.logviewer.LogViewerNavKey

/**
 * Translates the back stack into the destination model a caller outside the composition reads.
 *
 * The top-most entry that is not a popout wins: popouts render in their own windows and are
 * reported alongside whatever the main window shows.
 */
fun List<NavKey>.toHostDestination(): HostDestination {
    val poppedOut = poppedOutPlugins()
    return when (val top = lastOrNull { it !is PluginPopoutNavKey }) {
        is PluginNavKey -> HostDestination(
            kind = HostDestinationKind.PLUGIN,
            pluginId = top.pluginId,
            sessionId = top.sessionId,
            poppedOutPlugins = poppedOut,
        )

        is SettingsNavKey -> HostDestination(
            kind = HostDestinationKind.SETTINGS,
            settingsSection = top.initialPage.toHostSettingsSection(),
            poppedOutPlugins = poppedOut,
        )

        is McpToolsNavKey -> HostDestination(
            kind = HostDestinationKind.MCP_TOOLS,
            pluginId = top.pluginId,
            sessionId = top.sessionId,
            poppedOutPlugins = poppedOut,
        )

        InfoNavKey -> HostDestination(HostDestinationKind.INFO, poppedOutPlugins = poppedOut)

        LicensesNavKey -> HostDestination(HostDestinationKind.LICENSES, poppedOutPlugins = poppedOut)

        LogViewerNavKey -> HostDestination(HostDestinationKind.LOG_VIEWER, poppedOutPlugins = poppedOut)

        DisabledPluginNavKey -> HostDestination(HostDestinationKind.DISABLED_PLUGIN, poppedOutPlugins = poppedOut)

        else -> HostDestination(HostDestinationKind.HOME, poppedOutPlugins = poppedOut)
    }
}

fun List<NavKey>.poppedOutPlugins(): List<PoppedOutPlugin> = filterIsInstance<PluginPopoutNavKey>().map { PoppedOutPlugin(it.pluginId, it.sessionId) }

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
