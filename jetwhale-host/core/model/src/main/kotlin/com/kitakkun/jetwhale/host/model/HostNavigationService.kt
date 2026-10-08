package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A screen of the main host window a caller outside the composition can ask for. */
sealed interface HostNavigationRequest {
    data object Home : HostNavigationRequest

    /**
     * Opens [pluginId]; a null [sessionId] means "whichever session the drawer already has selected".
     * [followsAgent] marks a move the window makes on its own to follow an AI agent, which the window
     * announces so that a screen change nobody asked for is explained.
     */
    data class Plugin(val pluginId: String, val sessionId: String?, val followsAgent: Boolean) : HostNavigationRequest

    data class Settings(val page: HostSettingsPage) : HostNavigationRequest

    data object Info : HostNavigationRequest
    data object LogViewer : HostNavigationRequest

    /** Opens the MCP tools browser on [tab], showing every plugin and session. */
    data class McpTools(val tab: HostMcpToolsTab) : HostNavigationRequest
}

enum class HostSettingsSection {
    GENERAL,
    SERVER,
    AI_AGENTS,
    PLUGINS,
    ;

    val firstPage: HostSettingsPage get() = HostSettingsPage.entries.first { it.section == this }
}

/** A page of Settings, named as the settings menu labels it and listed in the menu's order. */
enum class HostSettingsPage(val section: HostSettingsSection) {
    APPEARANCE(HostSettingsSection.GENERAL),
    APPLICATION(HostSettingsSection.GENERAL),
    DEBUG_SERVER(HostSettingsSection.SERVER),
    SSL_CERTIFICATE(HostSettingsSection.SERVER),
    ADB_SUPPORT(HostSettingsSection.SERVER),
    MCP_SERVER(HostSettingsSection.AI_AGENTS),
    PERMISSIONS(HostSettingsSection.AI_AGENTS),
    ACTIVITY(HostSettingsSection.AI_AGENTS),
    INSTALLED_PLUGINS(HostSettingsSection.PLUGINS),
    ADD_PLUGINS(HostSettingsSection.PLUGINS),
    SECURITY(HostSettingsSection.PLUGINS),
}

/** A tab of the MCP tools browser, named as the browser labels it. */
enum class HostMcpToolsTab { TOOLS, HISTORY }

enum class HostDestinationKind { HOME, PLUGIN, DISABLED_PLUGIN, SETTINGS, INFO, LICENSES, LOG_VIEWER, MCP_TOOLS }

data class PoppedOutPlugin(val pluginId: String, val sessionId: String)

/**
 * What the main host window currently shows. Popped-out plugins live in their own windows and are
 * listed separately.
 *
 * [settingsPage] and [mcpToolsTab] are where that screen was opened; a page or tab picked inside it
 * afterwards is not tracked here.
 */
data class HostDestination(
    val kind: HostDestinationKind,
    val pluginId: String? = null,
    val sessionId: String? = null,
    val settingsPage: HostSettingsPage? = null,
    val mcpToolsTab: HostMcpToolsTab? = null,
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
