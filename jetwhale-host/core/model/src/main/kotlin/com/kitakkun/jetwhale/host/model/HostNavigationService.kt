package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A screen of the main host window a caller outside the composition can ask for. */
sealed interface HostNavigationRequest {
    data object Home : HostNavigationRequest

    /** Opens [pluginId]; a null [sessionId] means "whichever session the drawer already has selected". */
    data class Plugin(val pluginId: String, val sessionId: String?) : HostNavigationRequest

    data class Settings(val section: HostSettingsSection) : HostNavigationRequest
    data object Info : HostNavigationRequest
    data object LogViewer : HostNavigationRequest
}

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
 * Lets a caller outside the composition — the MCP server — drive the main window's navigation and
 * read back what is on screen.
 *
 * The back stack and the drawer's selection stay owned by the UI; this is only the channel between
 * the two. [requests] is delivered exactly once, so it must have a single collector.
 */
interface HostNavigationService {
    val requests: Flow<HostNavigationRequest>

    /** Null until the host window has composed and reported its first destination. */
    val currentView: StateFlow<HostViewState?>

    suspend fun navigate(request: HostNavigationRequest)

    fun updateDestination(destination: HostDestination)

    fun updateSelection(selectedSessionId: String?, selectedPluginId: String?)
}
