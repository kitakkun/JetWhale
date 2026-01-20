package com.kitakkun.jetwhale.host.settings.server

data class ServerSettingsScreenUiState(
    val serverState: ServerState,
    val editingPortText: String,
    val isApplyVisible: Boolean,
    val isApplyEnabled: Boolean,
    val showApplyConfirmDialog: Boolean,
)

sealed interface ServerState {
    data object Stopped : ServerState
    data class Running(val host: String, val port: Int) : ServerState
    data class Error(val reason: String) : ServerState
    data object Starting : ServerState
    data object Stopping : ServerState
}
