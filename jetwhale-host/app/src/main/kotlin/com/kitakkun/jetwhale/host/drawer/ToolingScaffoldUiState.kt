package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.DebugSession
import kotlinx.collections.immutable.ImmutableList

/**
 * What an AI agent is doing to the debugger right now.
 *
 * [operatingToolName] is the MCP tool currently being executed. It lingers briefly past the actual
 * call, because most calls finish too quickly to be seen otherwise.
 */
data class AiActivityUiState(
    val isAgentConnected: Boolean,
    val operatingToolName: String?,
) {
    val isOperating: Boolean get() = operatingToolName != null

    companion object {
        val Idle = AiActivityUiState(isAgentConnected = false, operatingToolName = null)
    }
}

data class ToolingScaffoldUiState(
    val selectedSessionId: String,
    val selectedPluginId: String,
    val sessions: ImmutableList<DebugSession>,
    val plugins: ImmutableList<DrawerPluginItemUiState>,
    val hasFailedJars: Boolean,
    val aiActivity: AiActivityUiState,
) {
    val selectedSession: DebugSession? get() = sessions.find { it.id == selectedSessionId }
}
