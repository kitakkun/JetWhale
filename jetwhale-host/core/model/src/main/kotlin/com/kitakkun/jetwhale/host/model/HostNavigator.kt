package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow

enum class HostSettingsSection { GENERAL, SERVER, AI_AGENTS, PLUGINS }

/**
 * MCP_TOOLS is reported but cannot be requested: the tool browser exists so a person can watch what
 * an agent is doing, so an agent has no reason to send itself there.
 */
enum class HostDestinationKind { HOME, PLUGIN, DISABLED_PLUGIN, SETTINGS, INFO, LICENSES, LOG_VIEWER, MCP_TOOLS }

data class PoppedOutPlugin(val pluginId: String, val sessionId: String)

/** What the main host window currently shows. Popped-out plugins live in their own windows and are listed separately. */
data class HostDestination(
    val kind: HostDestinationKind,
    val pluginId: String? = null,
    val sessionId: String? = null,
    val settingsSection: HostSettingsSection? = null,
    val poppedOutPlugins: List<PoppedOutPlugin> = emptyList(),
)

data class HostViewState(
    val destination: HostDestination,
    val selectedSessionId: String?,
    val selectedPluginId: String?,
)

/**
 * The main window's navigation surface for a caller outside the composition — the MCP server.
 *
 * One semantic method per screen an agent may ask for, plus the read side that says what the window
 * ended up showing, so a caller can confirm its own request landed rather than assume it did. The
 * back stack itself stays owned by the UI; this is only the channel between the two.
 *
 * Requests are queued, so one sent before the window has composed is delivered once it has.
 */
interface HostNavigator {
    /** Null until the host window has composed and published its first back stack. */
    val currentView: StateFlow<HostViewState?>

    fun navigateHome()

    /** Opens [pluginId]; a null [sessionId] means "whichever session the drawer already has selected". */
    fun navigateToPlugin(pluginId: String, sessionId: String?)

    fun navigateToSettings(section: HostSettingsSection)

    fun navigateToInfo()

    fun navigateToLogViewer()
}
