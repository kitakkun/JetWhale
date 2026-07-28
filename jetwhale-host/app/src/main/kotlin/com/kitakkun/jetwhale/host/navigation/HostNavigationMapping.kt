package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.model.PoppedOutPlugin
import com.kitakkun.jetwhale.host.settings.SettingsScreenSegmentedMenu

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
            settingsSection = top.initialMenu.toHostSettingsSection(),
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

fun HostSettingsSection.toSegmentedMenu(): SettingsScreenSegmentedMenu = when (this) {
    HostSettingsSection.GENERAL -> SettingsScreenSegmentedMenu.General
    HostSettingsSection.SERVER -> SettingsScreenSegmentedMenu.Server
    HostSettingsSection.PLUGINS -> SettingsScreenSegmentedMenu.Plugins
}

private fun SettingsScreenSegmentedMenu.toHostSettingsSection(): HostSettingsSection = when (this) {
    SettingsScreenSegmentedMenu.General -> HostSettingsSection.GENERAL
    SettingsScreenSegmentedMenu.Server -> HostSettingsSection.SERVER
    SettingsScreenSegmentedMenu.Plugins -> HostSettingsSection.PLUGINS
}
