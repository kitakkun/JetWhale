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
import com.kitakkun.jetwhale.host.model.McpHostGroupPermissionParams
import com.kitakkun.jetwhale.host.model.McpPermissionsSnapshot
import com.kitakkun.jetwhale.host.model.McpPluginPermissionParams
import com.kitakkun.jetwhale.host.model.McpPluginToolPermissionParams
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.SslCertificateEntry
import com.kitakkun.jetwhale.host.settings.SettingsPresenterContext
import soil.query.compose.rememberMutation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
context(presenterContext: SettingsPresenterContext)
fun serverSettingsScreenPresenter(
    screenChannel: ScreenChannel<ServerSettingsScreenAction, Nothing>,
    serverStatus: DebugWebSocketServerStatus,
    mcpPermissionsSnapshot: McpPermissionsSnapshot,
    mcpServerStatus: McpServerStatus,
    debuggerSettings: DebuggerBehaviorSettings,
    sslCertificates: List<SslCertificateEntry>,
): ServerSettingsScreenUiState {
    val certificates by remember(sslCertificates) {
        derivedStateOf {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sslCertificates.map { entry ->
                CertificateUiEntry(
                    id = entry.id,
                    name = entry.name,
                    createdAt = dateFormat.format(Date(entry.createdAt)),
                    caCertificatePem = entry.caCertificatePem,
                    isActive = entry.isActive,
                )
            }
        }
    }

    var editingDebugPortText by remember { mutableStateOf(debuggerSettings.serverPort.toString()) }
    var certificateDetailDialogEntry by remember { mutableStateOf<CertificateUiEntry?>(null) }
    var editingMcpPortText by remember { mutableStateOf(debuggerSettings.mcpServerPort.toString()) }
    var showDebugApplyConfirmDialog by remember { mutableStateOf(false) }
    var showMcpApplyConfirmDialog by remember { mutableStateOf(false) }

    val debugPortMutation = rememberMutation(presenterContext.serverPortMutationKey)
    val mcpPortMutation = rememberMutation(presenterContext.mcpServerPortMutationKey)
    val generateCertificateMutation = rememberMutation(presenterContext.generateSslCertificateMutationKey)
    val activateCertificateMutation = rememberMutation(presenterContext.activateSslCertificateMutationKey)
    val deleteCertificateMutation = rememberMutation(presenterContext.deleteSslCertificateMutationKey)
    val hostGroupPermissionMutation = rememberMutation(presenterContext.mcpHostGroupPermissionMutationKey)
    val pluginInspectPermissionMutation = rememberMutation(presenterContext.mcpPluginInspectPermissionMutationKey)
    val pluginInteractPermissionMutation = rememberMutation(presenterContext.mcpPluginInteractPermissionMutationKey)
    val pluginToolPermissionMutation = rememberMutation(presenterContext.mcpPluginToolPermissionMutationKey)

    val savedDebugPortText by rememberUpdatedState(debuggerSettings.serverPort.toString())
    val savedMcpPortText by rememberUpdatedState(debuggerSettings.mcpServerPort.toString())

    val isDebugDirty by remember { derivedStateOf { editingDebugPortText != savedDebugPortText } }
    val isMcpDirty by remember { derivedStateOf { editingMcpPortText != savedMcpPortText } }

    val parsedDebugPort by remember { derivedStateOf { editingDebugPortText.toIntOrNull() } }
    val parsedMcpPort by remember { derivedStateOf { editingMcpPortText.toIntOrNull() } }

    val isDebugPortValid by remember { derivedStateOf { parsedDebugPort != null && parsedDebugPort in 1..65535 } }
    val isMcpPortValid by remember { derivedStateOf { parsedMcpPort != null && parsedMcpPort in 1..65535 } }

    // A failed start leaves the port setting untouched, so gating the button on dirtiness alone
    // would make retrying the same port impossible without editing it away and back again.
    val isDebugStartFailed by rememberUpdatedState(serverStatus is DebugWebSocketServerStatus.Error)
    val isMcpStartFailed by rememberUpdatedState(mcpServerStatus is McpServerStatus.Error)

    // The snippets must describe the endpoint an agent can actually reach right now, so they follow
    // the running server rather than the (possibly unapplied) port text field.
    val runningMcpStatus by rememberUpdatedState(mcpServerStatus as? McpServerStatus.Running)
    val savedMcpPort by rememberUpdatedState(debuggerSettings.mcpServerPort)
    val mcpEndpointUrl by remember {
        derivedStateOf {
            val host = runningMcpStatus?.host ?: "localhost"
            val port = runningMcpStatus?.port ?: savedMcpPort
            "http://$host:$port/sse"
        }
    }

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
                if (!isDebugPortValid) return@ActionEffect
                when {
                    isDebugDirty -> showDebugApplyConfirmDialog = true

                    // Nothing is listening after a failed start, so there are no connected clients
                    // a restart could disrupt — retry without asking.
                    isDebugStartFailed -> debugPortMutation.mutateAsync(parsedDebugPort ?: return@ActionEffect)
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
                if (!isMcpPortValid) return@ActionEffect
                when {
                    isMcpDirty -> showMcpApplyConfirmDialog = true
                    isMcpStartFailed -> mcpPortMutation.mutateAsync(parsedMcpPort ?: return@ActionEffect)
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

            is ServerSettingsScreenAction.SetHostGroupAllowed -> {
                hostGroupPermissionMutation.mutateAsync(McpHostGroupPermissionParams(action.group, action.allowed))
            }

            is ServerSettingsScreenAction.SetPluginInspectAllowed -> {
                pluginInspectPermissionMutation.mutateAsync(McpPluginPermissionParams(action.pluginId, action.allowed))
            }

            is ServerSettingsScreenAction.SetPluginInteractAllowed -> {
                pluginInteractPermissionMutation.mutateAsync(McpPluginPermissionParams(action.pluginId, action.allowed))
            }

            is ServerSettingsScreenAction.SetPluginToolAllowed -> {
                pluginToolPermissionMutation.mutateAsync(McpPluginToolPermissionParams(action.toolName, action.allowed))
            }

            ServerSettingsScreenAction.AddCertificate -> {
                // A newly generated certificate becomes the active one; the running TLS server
                // hot-swaps to it automatically.
                generateCertificateMutation.mutateAsync(null)
            }

            is ServerSettingsScreenAction.SetActiveCertificate -> {
                activateCertificateMutation.mutateAsync(action.id)
            }

            is ServerSettingsScreenAction.DeleteCertificate -> {
                deleteCertificateMutation.mutateAsync(action.id)
                if (certificateDetailDialogEntry?.id == action.id) {
                    certificateDetailDialogEntry = null
                }
            }

            is ServerSettingsScreenAction.ShowCertificateDetail -> {
                certificateDetailDialogEntry = certificates.find { it.id == action.id }
            }

            ServerSettingsScreenAction.DismissCertificateDetailDialog -> {
                certificateDetailDialogEntry = null
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
                wssPort = serverStatus.wssPort,
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
        mcpClaudeCodeCommand = "claude mcp add --transport sse jetwhale $mcpEndpointUrl",
        mcpJsonConfig = """
            {
              "mcpServers": {
                "jetwhale": {
                  "type": "sse",
                  "url": "$mcpEndpointUrl"
                }
              }
            }
        """.trimIndent(),
        mcpPermissions = McpPermissionsUiState(
            allowedHostGroups = mcpPermissionsSnapshot.permissions.allowedHostGroups,
            plugins = mcpPermissionsSnapshot.plugins.map { plugin ->
                McpPluginPermissionUiState(
                    pluginId = plugin.pluginId,
                    displayName = plugin.displayName,
                    inspectAllowed = plugin.pluginId !in mcpPermissionsSnapshot.permissions.pluginsDeniedInspect,
                    interactAllowed = plugin.pluginId !in mcpPermissionsSnapshot.permissions.pluginsDeniedInteract,
                    tools = plugin.tools.map { tool ->
                        McpPluginToolUiState(
                            toolName = tool.name,
                            allowed = tool.name !in mcpPermissionsSnapshot.permissions.deniedPluginTools,
                        )
                    },
                )
            },
        ),
        isDebugApplyVisible = isDebugDirty || isDebugStartFailed,
        isMcpApplyVisible = isMcpDirty || isMcpStartFailed,
        isDebugApplyEnabled = isDebugPortValid && (isDebugDirty || isDebugStartFailed),
        isMcpApplyEnabled = isMcpPortValid && (isMcpDirty || isMcpStartFailed),
        isDebugRetry = isDebugStartFailed && !isDebugDirty,
        isMcpRetry = isMcpStartFailed && !isMcpDirty,
        showDebugApplyConfirmDialog = showDebugApplyConfirmDialog,
        showMcpApplyConfirmDialog = showMcpApplyConfirmDialog,
        certificates = certificates,
        certificateDetailDialogEntry = certificateDetailDialogEntry,
    )
}
