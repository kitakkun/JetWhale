package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.EventFlow
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus

@Composable
fun serverSettingsScreenPresenter(
    eventFlow: EventFlow<ServerSettingsScreenEvent>,
    serverStatus: DebugWebSocketServerStatus,
): ServerSettingsScreenUiState {
    return ServerSettingsScreenUiState(
        serverState = when (serverStatus) {
            is DebugWebSocketServerStatus.Stopped -> ServerState.Stopped
            is DebugWebSocketServerStatus.Starting -> ServerState.Starting
            is DebugWebSocketServerStatus.Started -> ServerState.Running(
                host = serverStatus.host,
                port = serverStatus.port,
            )

            is DebugWebSocketServerStatus.Error -> ServerState.Error(
                reason = serverStatus.message,
            )

            is DebugWebSocketServerStatus.Stopping -> ServerState.Stopping
        },
        editingPort = 0,
    )
}
