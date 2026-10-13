package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.unit.Dp
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.McpClientSetup
import kotlinx.collections.immutable.ImmutableList

/**
 * What an AI agent is doing to the debugger right now.
 *
 * [operatingToolName] is the MCP tool currently being executed. It lingers briefly past the actual
 * call, because most calls finish too quickly to be seen otherwise.
 *
 * @property operatingToolShortName [operatingToolName] short enough for one line: a plugin's tool
 *   prefixed with the last segment of its plugin id (`mirror.tap`), a host tool as it is
 *   (`jetwhale.click`).
 * @property operatingPluginName The name of the plugin [operatingToolName] belongs to, or `null` for a
 *   host tool, which targets no plugin.
 * @property operatingAppName The app the call targets, or `null` when it targets none or a tool that
 *   needs no app.
 * @property isFollowModeOn Whether the window is set to move to whatever plugin an agent operates.
 * @property mcpServer Whether an agent could connect at all, and how, for the indicator to say while
 *   none is connected.
 */
data class AiActivityUiState(
    val isAgentConnected: Boolean,
    val operatingToolName: String?,
    val operatingToolShortName: String?,
    val operatingPluginName: String?,
    val operatingAppName: String?,
    val isFollowModeOn: Boolean,
    val mcpServer: McpServerAvailability,
) {
    val isOperating: Boolean get() = operatingToolName != null

    companion object {
        val Idle = AiActivityUiState(
            isAgentConnected = false,
            operatingToolName = null,
            operatingToolShortName = null,
            operatingPluginName = null,
            operatingAppName = null,
            isFollowModeOn = false,
            mcpServer = McpServerAvailability.Off(reason = null),
        )
    }
}

/** Whether the host's MCP server is there for an agent to connect to. */
sealed interface McpServerAvailability {
    /** Not running: stopped, or failed to start with [reason]. */
    data class Off(val reason: String?) : McpServerAvailability

    data object Starting : McpServerAvailability

    /** Running, reachable as [setup] describes. */
    data class Ready(val setup: McpClientSetup) : McpServerAvailability
}

data class ToolingScaffoldUiState(
    val selectedSessionId: String,
    val selectedPluginId: String,
    val sessions: ImmutableList<DebugSession>,
    val plugins: ImmutableList<DrawerPluginItemUiState>,
    val hasFailedJars: Boolean,
    val aiActivity: AiActivityUiState,
    val sidebarWidth: Dp,
) {
    val selectedSession: DebugSession? get() = sessions.find { it.id == selectedSessionId }

    /**
     * The session the drawer's plugin [pluginId] opens in: [HostSession] for a plugin that needs no
     * app, otherwise the selected app, or null while no app is selected.
     */
    fun sessionIdFor(pluginId: String): String? = when (plugins.find { it.id == pluginId }?.needsApp) {
        false -> HostSession.ID
        else -> selectedSession?.id
    }
}
