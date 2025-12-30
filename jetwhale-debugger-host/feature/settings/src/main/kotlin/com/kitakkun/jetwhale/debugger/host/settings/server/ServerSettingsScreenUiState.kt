package com.kitakkun.jetwhale.debugger.host.settings.server

data class ServerSettingsScreenUiState(
    val serverState: ServerState,
    val editingPort: Int,
) {
    val canEditPort: Boolean get() = serverState is ServerState.Stopped || serverState is ServerState.Error
}

sealed interface ServerState {
    data object Stopped : ServerState
    data class Running(val host: String, val port: Int) : ServerState
    data class Error(val reason: String) : ServerState
    data object Starting : ServerState
    data object Stopping : ServerState
}
