package com.kitakkun.jetwhale.host.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/**
 * A top-level grouping in the settings list. Sections are containers only; pages hold the content.
 *
 * The grouping follows what a setting is *about* rather than where it used to live. ADB sits under
 * Connection because port wiring is how a debuggee reaches the host, not an appearance concern; and
 * the MCP server has a section of its own because it answers a different question from the debug
 * server — who may drive this tool, rather than how an app connects to it.
 */
@Serializable
enum class SettingsScreenSection(
    val labelTextRes: StringResource,
    val icon: ImageVector,
) {
    General(
        labelTextRes = Res.string.general,
        icon = Icons.Default.Info,
    ),
    Connection(
        labelTextRes = Res.string.settings_section_connection,
        icon = Icons.Default.Computer,
    ),
    AiAgents(
        labelTextRes = Res.string.settings_section_ai_agents,
        icon = Icons.Default.SmartToy,
    ),
    Plugins(
        labelTextRes = Res.string.plugins,
        icon = Icons.Default.Work,
    ),
    ;

    /** Selecting a section means selecting where it starts; a section has no page of its own. */
    val firstPage: SettingsScreenPage get() = SettingsScreenPage.entries.first { it.section == this }
}

/** Which section Root already subscribes to a page's data. Independent of where the menu files it. */
enum class SettingsScreenPageOwner { General, Server, Plugin }

/**
 * One page of settings — the unit the detail pane renders and the navigation list selects.
 *
 * Promoting the old headings to pages keeps each one short enough to take in at a glance, and gives
 * the ones that keep growing room to do so.
 */
@Serializable
enum class SettingsScreenPage(
    val section: SettingsScreenSection,
    val labelTextRes: StringResource,
    val owner: SettingsScreenPageOwner,
) {
    Appearance(SettingsScreenSection.General, Res.string.appearance, SettingsScreenPageOwner.General),

    /** Version, update checks, the app data directory and the log viewer: this install of the host. */
    Application(SettingsScreenSection.General, Res.string.settings_page_application, SettingsScreenPageOwner.General),

    DebugServer(SettingsScreenSection.Connection, Res.string.debug_server_label, SettingsScreenPageOwner.Server),
    SslCertificate(SettingsScreenSection.Connection, Res.string.ssl_certificate, SettingsScreenPageOwner.Server),

    /** Auto port wiring and where adb was found — one topic that used to be two headings. */
    Adb(SettingsScreenSection.Connection, Res.string.adb_support, SettingsScreenPageOwner.General),

    McpServer(SettingsScreenSection.AiAgents, Res.string.mcp_server_label, SettingsScreenPageOwner.Server),

    InstalledPlugins(SettingsScreenSection.Plugins, Res.string.installed_plugins, SettingsScreenPageOwner.Plugin),

    /** Every way a plugin gets in: the official catalog, a local jar, or Maven coordinates. */
    AddPlugins(SettingsScreenSection.Plugins, Res.string.settings_page_add_plugins, SettingsScreenPageOwner.Plugin),

    PluginSecurity(SettingsScreenSection.Plugins, Res.string.plugin_security, SettingsScreenPageOwner.Plugin),
}
