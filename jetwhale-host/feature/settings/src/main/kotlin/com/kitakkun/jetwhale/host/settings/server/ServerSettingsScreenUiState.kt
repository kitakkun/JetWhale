package com.kitakkun.jetwhale.host.settings.server

data class ServerSettingsScreenUiState(
    val debugServerState: ServerState,
    val mcpServerState: ServerState,
    val editingDebugPortText: String,
    val editingMcpPortText: String,
    val isDebugApplyVisible: Boolean,
    val isMcpApplyVisible: Boolean,
    val isDebugApplyEnabled: Boolean,
    val isMcpApplyEnabled: Boolean,
    val showDebugApplyConfirmDialog: Boolean,
    val showMcpApplyConfirmDialog: Boolean,
)

sealed interface ServerState {
    data object Stopped : ServerState
    data class Running(val host: String, val port: Int) : ServerState
    data class Error(val reason: String) : ServerState
    data object Starting : ServerState
    data object Stopping : ServerState
}
