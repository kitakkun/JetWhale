package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.EventEffect
import com.kitakkun.jetwhale.host.architecture.EventFlow
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerBehaviorSettings
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext
import soil.query.compose.rememberMutation

context(screenContext: SettingsScreenContext)
@Composable
fun serverSettingsScreenPresenter(
    eventFlow: EventFlow<ServerSettingsScreenEvent>,
    serverStatus: DebugWebSocketServerStatus,
    debuggerSettings: DebuggerBehaviorSettings,
): ServerSettingsScreenUiState {
    var editingPortText by remember { mutableStateOf(debuggerSettings.serverPort.toString()) }
    var showApplyConfirmDialog by remember { mutableStateOf(false) }

    val serverPortMutation = rememberMutation(screenContext.serverPortMutationKey)
    val savedPortText by rememberUpdatedState(debuggerSettings.serverPort.toString())
    val isDirty by remember { derivedStateOf { editingPortText != savedPortText } }
    val parsedPort by remember { derivedStateOf { editingPortText.toIntOrNull() } }
    val isPortValid by remember { derivedStateOf { parsedPort != null && parsedPort in 1..65535 } }

    LaunchedEffect(serverStatus) {
        if (serverStatus is DebugWebSocketServerStatus.Started) {
            editingPortText = serverStatus.port.toString()
        }
    }

    EventEffect(eventFlow) { event ->
        when (event) {
            is ServerSettingsScreenEvent.ChangePortText -> {
                editingPortText = event.text.filter { it.isDigit() }
            }

            ServerSettingsScreenEvent.ApplyPortChange -> {
                if (isPortValid && isDirty) {
                    showApplyConfirmDialog = true
                }
            }

            ServerSettingsScreenEvent.ConfirmApplyPortChange -> {
                val parsedPort = parsedPort ?: return@EventEffect
                if (!isPortValid) return@EventEffect
                showApplyConfirmDialog = false
                serverPortMutation.mutate(parsedPort)
            }

            ServerSettingsScreenEvent.DismissApplyPortDialog -> {
                showApplyConfirmDialog = false
            }
        }
    }

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
        editingPortText = editingPortText,
        isApplyVisible = isDirty,
        isApplyEnabled = isPortValid && isDirty,
        showApplyConfirmDialog = showApplyConfirmDialog,
    )
}
