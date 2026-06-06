package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerBehaviorSettings
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.settings.SettingsPresenterContext
import soil.query.compose.rememberMutation

@Composable
context(presenterContext: SettingsPresenterContext)
fun serverSettingsScreenPresenter(
    screenChannel: ScreenChannel<ServerSettingsScreenAction, Nothing>,
    serverStatus: DebugWebSocketServerStatus,
    mcpServerStatus: McpServerStatus,
    debuggerSettings: DebuggerBehaviorSettings,
): ServerSettingsScreenUiState {
    var editingDebugPortText by remember { mutableStateOf(debuggerSettings.serverPort.toString()) }
    var editingMcpPortText by remember { mutableStateOf(debuggerSettings.mcpServerPort.toString()) }
    var showDebugApplyConfirmDialog by remember { mutableStateOf(false) }
    var showMcpApplyConfirmDialog by remember { mutableStateOf(false) }

    val debugPortMutation = rememberMutation(presenterContext.serverPortMutationKey)
    val mcpPortMutation = rememberMutation(presenterContext.mcpServerPortMutationKey)

    val savedDebugPortText by rememberUpdatedState(debuggerSettings.serverPort.toString())
    val savedMcpPortText by rememberUpdatedState(debuggerSettings.mcpServerPort.toString())

    val isDebugDirty by remember { derivedStateOf { editingDebugPortText != savedDebugPortText } }
    val isMcpDirty by remember { derivedStateOf { editingMcpPortText != savedMcpPortText } }

    val parsedDebugPort by remember { derivedStateOf { editingDebugPortText.toIntOrNull() } }
    val parsedMcpPort by remember { derivedStateOf { editingMcpPortText.toIntOrNull() } }

    val isDebugPortValid by remember { derivedStateOf { parsedDebugPort != null && parsedDebugPort in 1..65535 } }
    val isMcpPortValid by remember { derivedStateOf { parsedMcpPort != null && parsedMcpPort in 1..65535 } }

    LaunchedEffect(serverStatus) {
        if (serverStatus is DebugWebSocketServerStatus.Started) {
            editingDebugPortText = serverStatus.port.toString()
        }
    }

    LaunchedEffect(mcpServerStatus) {
        if (mcpServerStatus is McpServerStatus.Running) {
            editingMcpPortText = mcpServerStatus.port.toString()
        }
    }

    ActionEffect(screenChannel) { action ->
        when (action) {
            is ServerSettingsScreenAction.ChangeDebugPortText -> {
                editingDebugPortText = action.text.filter { it.isDigit() }
            }

            ServerSettingsScreenAction.ApplyDebugPortChange -> {
                if (isDebugPortValid && isDebugDirty) {
                    showDebugApplyConfirmDialog = true
                }
            }

            ServerSettingsScreenAction.ConfirmApplyDebugPortChange -> {
                val port = parsedDebugPort ?: return@ActionEffect
                if (!isDebugPortValid) return@ActionEffect
                showDebugApplyConfirmDialog = false
                debugPortMutation.mutateAsync(port)
            }

            ServerSettingsScreenAction.DismissApplyDebugPortDialog -> {
                showDebugApplyConfirmDialog = false
            }

            is ServerSettingsScreenAction.ChangeMcpPortText -> {
                editingMcpPortText = action.text.filter { it.isDigit() }
            }

            ServerSettingsScreenAction.ApplyMcpPortChange -> {
                if (isMcpPortValid && isMcpDirty) {
                    showMcpApplyConfirmDialog = true
                }
            }

            ServerSettingsScreenAction.ConfirmApplyMcpPortChange -> {
                val port = parsedMcpPort ?: return@ActionEffect
                if (!isMcpPortValid) return@ActionEffect
                showMcpApplyConfirmDialog = false
                mcpPortMutation.mutateAsync(port)
            }

            ServerSettingsScreenAction.DismissApplyMcpPortDialog -> {
                showMcpApplyConfirmDialog = false
            }
        }
    }

    return ServerSettingsScreenUiState(
        debugServerState = when (serverStatus) {
            is DebugWebSocketServerStatus.Stopped -> ServerState.Stopped

            is DebugWebSocketServerStatus.Starting -> ServerState.Starting

            is DebugWebSocketServerStatus.Started -> ServerState.Running(
                host = serverStatus.host,
                port = serverStatus.port,
            )

            is DebugWebSocketServerStatus.Error -> ServerState.Error(reason = serverStatus.message)

            is DebugWebSocketServerStatus.Stopping -> ServerState.Stopping
        },
        mcpServerState = when (mcpServerStatus) {
            is McpServerStatus.Stopped -> ServerState.Stopped

            is McpServerStatus.Starting -> ServerState.Starting

            is McpServerStatus.Running -> ServerState.Running(
                host = mcpServerStatus.host,
                port = mcpServerStatus.port,
            )

            is McpServerStatus.Error -> ServerState.Error(reason = mcpServerStatus.message)

            is McpServerStatus.Stopping -> ServerState.Stopping
        },
        editingDebugPortText = editingDebugPortText,
        editingMcpPortText = editingMcpPortText,
        isDebugApplyVisible = isDebugDirty,
        isMcpApplyVisible = isMcpDirty,
        isDebugApplyEnabled = isDebugPortValid && isDebugDirty,
        isMcpApplyEnabled = isMcpPortValid && isMcpDirty,
        showDebugApplyConfirmDialog = showDebugApplyConfirmDialog,
        showMcpApplyConfirmDialog = showMcpApplyConfirmDialog,
    )
}
