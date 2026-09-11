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
    /** Whether the window is set to move to whatever plugin an agent operates. */
    val isFollowModeOn: Boolean,
    /**
     * Whether what is on the main window's screen is there under the agent's direction right now —
     * the mode is on and the call in flight names a plugin that this window shows or has just moved
     * to. The follow banner turns to its warning state and names the tool for as long as this holds.
     * A plugin popped out into its own window is watched there, so a call to it does not count.
     */
    val isFollowingOperation: Boolean,
) {
    val isOperating: Boolean get() = operatingToolName != null

    /**
     * Whether the strip that announces a follow is on screen. It stays up for as long as a follow
     * could happen — the mode is on and an agent is connected — rather than for each call, so the
     * plugin under it keeps its place through a burst of operations instead of jumping at every one.
     */
    val showsFollowBanner: Boolean get() = isFollowModeOn && isAgentConnected

    companion object {
        val Idle = AiActivityUiState(
            isAgentConnected = false,
            operatingToolName = null,
            isFollowModeOn = false,
            isFollowingOperation = false,
        )
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
