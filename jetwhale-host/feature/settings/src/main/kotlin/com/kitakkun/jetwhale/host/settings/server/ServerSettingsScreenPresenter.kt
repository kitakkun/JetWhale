package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.model.DebugServerSettings
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.McpHostGroupPermissionParams
import com.kitakkun.jetwhale.host.model.McpPermissionsSnapshot
import com.kitakkun.jetwhale.host.model.McpPluginPermissionParams
import com.kitakkun.jetwhale.host.model.McpPluginToolPermissionParams
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.SslCertificateEntry
import com.kitakkun.jetwhale.host.settings.SettingsPresenterContext
import soil.query.compose.MutationObject
import soil.query.compose.rememberMutation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BINDABLE_PORTS = 1..65535

@Composable
context(presenterContext: SettingsPresenterContext)
fun serverSettingsScreenPresenter(
    screenChannel: ScreenChannel<ServerSettingsScreenAction, Nothing>,
    serverStatus: DebugWebSocketServerStatus,
    mcpPermissionsSnapshot: McpPermissionsSnapshot,
    mcpServerStatus: McpServerStatus,
    debugServerSettings: DebugServerSettings,
    mcpServerPort: Int,
    sslCertificates: List<SslCertificateEntry>,
): ServerSettingsScreenUiState {
    val editing = remember { ServerSettingsEditingState(debugServerSettings, mcpServerPort) }
    editing.savedDebugServerSettings = debugServerSettings
    editing.savedMcpServerPort = mcpServerPort
    editing.serverStatus = serverStatus
    editing.mcpServerStatus = mcpServerStatus
    editing.sslCertificates = sslCertificates

    val mutations = rememberServerSettingsMutations()

    LaunchedEffect(serverStatus) {
        if (serverStatus is DebugWebSocketServerStatus.Started) {
            editing.debugPortText = serverStatus.port.toString()
        }
    }

    // A Started status carries a null wss port both when the connector is switched off and when it
    // was asked for but could not bind, so reading the switch back off the status would silently
    // turn wss off in the second case and the next Apply would persist that. The store is the whole
    // truth here; launch overrides reach these values through the repository too.
    LaunchedEffect(debugServerSettings.wssEnabled, debugServerSettings.wssPort) {
        editing.wssEnabled = debugServerSettings.wssEnabled
        editing.wssPortText = debugServerSettings.wssPort.toString()
    }

    LaunchedEffect(mcpServerStatus) {
        if (mcpServerStatus is McpServerStatus.Running) {
            editing.mcpPortText = mcpServerStatus.port.toString()
        }
    }

    ActionEffect(screenChannel) { action -> editing.applyAction(action, mutations) }

    return editing.toUiState(mcpPermissionsSnapshot)
}

/**
 * What the Debug Server page is showing and what the user has typed into it but not applied yet.
 *
 * Every field the action handler and the rendered state read lives here, because the handler is
 * installed once per screen channel and would otherwise keep reading the first composition's values.
 */
@Stable
private class ServerSettingsEditingState(
    savedDebugServerSettings: DebugServerSettings,
    savedMcpServerPort: Int,
) {
    var savedDebugServerSettings: DebugServerSettings by mutableStateOf(savedDebugServerSettings)
    var savedMcpServerPort: Int by mutableStateOf(savedMcpServerPort)
    var serverStatus: DebugWebSocketServerStatus by mutableStateOf(DebugWebSocketServerStatus.Stopped)
    var mcpServerStatus: McpServerStatus by mutableStateOf(McpServerStatus.Stopped)
    var sslCertificates: List<SslCertificateEntry> by mutableStateOf(emptyList())

    var debugPortText: String by mutableStateOf(savedDebugServerSettings.serverPort.toString())
    var wssPortText: String by mutableStateOf(savedDebugServerSettings.wssPort.toString())
    var wssEnabled: Boolean by mutableStateOf(savedDebugServerSettings.wssEnabled)
    var mcpPortText: String by mutableStateOf(savedMcpServerPort.toString())
    var showDebugApplyConfirmDialog: Boolean by mutableStateOf(false)
    var showMcpApplyConfirmDialog: Boolean by mutableStateOf(false)
    var certificateDetailDialogEntry: CertificateUiEntry? by mutableStateOf(null)

    val certificates: List<CertificateUiEntry>
        get() {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return sslCertificates.map { entry ->
                CertificateUiEntry(
                    id = entry.id,
                    name = entry.name,
                    createdAt = dateFormat.format(Date(entry.createdAt)),
                    caCertificatePem = entry.caCertificatePem,
                    isActive = entry.isActive,
                )
            }
        }

    private val isDebugDirty: Boolean
        get() = debugPortText != savedDebugServerSettings.serverPort.toString() ||
            wssPortText != savedDebugServerSettings.wssPort.toString() ||
            wssEnabled != savedDebugServerSettings.wssEnabled

    private val isMcpDirty: Boolean get() = mcpPortText != savedMcpServerPort.toString()

    private val parsedDebugPort: Int? get() = debugPortText.toIntOrNull()?.takeIf(BINDABLE_PORTS::contains)

    // Validated even while the connector is switched off: the port is stored either way, and a
    // rejected value would otherwise only surface on the restart that switches wss back on.
    private val parsedWssPort: Int? get() = wssPortText.toIntOrNull()?.takeIf(BINDABLE_PORTS::contains)

    private val parsedMcpPort: Int? get() = mcpPortText.toIntOrNull()

    private val isMcpPortValid: Boolean get() = parsedMcpPort in BINDABLE_PORTS

    private val debugServerSettingsError: DebugServerSettingsError?
        get() = when {
            parsedDebugPort == null || parsedWssPort == null -> DebugServerSettingsError.InvalidPort
            wssEnabled && parsedDebugPort == parsedWssPort -> DebugServerSettingsError.PortConflict
            else -> null
        }

    /** The edited debug server configuration, or null while it is not one the server could bind. */
    private val editedDebugServerSettings: DebugServerSettings?
        get() {
            if (debugServerSettingsError != null) return null
            val serverPort = parsedDebugPort ?: return null
            val wssPort = parsedWssPort ?: return null
            return DebugServerSettings(serverPort = serverPort, wssPort = wssPort, wssEnabled = wssEnabled)
        }

    // A failed start leaves the port setting untouched, so gating Apply on dirtiness alone would
    // make retrying the same port impossible without editing it away and back again.
    private val isDebugStartFailed: Boolean get() = serverStatus is DebugWebSocketServerStatus.Error

    private val isMcpStartFailed: Boolean get() = mcpServerStatus is McpServerStatus.Error

    suspend fun applyAction(action: ServerSettingsScreenAction, mutations: ServerSettingsMutations) {
        when (action) {
            is ServerSettingsScreenAction.ChangeDebugPortText -> debugPortText = action.text.filter(Char::isDigit)

            is ServerSettingsScreenAction.ChangeWssPortText -> wssPortText = action.text.filter(Char::isDigit)

            is ServerSettingsScreenAction.ChangeWssEnabled -> wssEnabled = action.enabled

            is ServerSettingsScreenAction.ApplyDebugServerSettingsChange -> {
                val settings = editedDebugServerSettings ?: return
                when {
                    isDebugDirty -> showDebugApplyConfirmDialog = true

                    // Nothing is listening after a failed start, so there are no connected clients
                    // a restart could disrupt — retry without asking.
                    isDebugStartFailed -> mutations.debugServerSettings.mutateAsync(settings)
                }
            }

            is ServerSettingsScreenAction.ConfirmApplyDebugServerSettingsChange -> {
                val settings = editedDebugServerSettings ?: return
                showDebugApplyConfirmDialog = false
                mutations.debugServerSettings.mutateAsync(settings)
            }

            is ServerSettingsScreenAction.DismissApplyDebugServerSettingsDialog -> showDebugApplyConfirmDialog = false

            is ServerSettingsScreenAction.ChangeMcpPortText -> mcpPortText = action.text.filter(Char::isDigit)

            is ServerSettingsScreenAction.ApplyMcpPortChange -> {
                if (!isMcpPortValid) return
                when {
                    isMcpDirty -> showMcpApplyConfirmDialog = true
                    isMcpStartFailed -> mutations.mcpPort.mutateAsync(parsedMcpPort ?: return)
                }
            }

            is ServerSettingsScreenAction.ConfirmApplyMcpPortChange -> {
                val port = parsedMcpPort ?: return
                if (!isMcpPortValid) return
                showMcpApplyConfirmDialog = false
                mutations.mcpPort.mutateAsync(port)
            }

            is ServerSettingsScreenAction.DismissApplyMcpPortDialog -> showMcpApplyConfirmDialog = false

            is ServerSettingsScreenAction.SetHostGroupAllowed ->
                mutations.hostGroupPermission.mutateAsync(McpHostGroupPermissionParams(action.group, action.allowed))

            is ServerSettingsScreenAction.SetPluginInspectAllowed ->
                mutations.pluginInspectPermission.mutateAsync(McpPluginPermissionParams(action.pluginId, action.allowed))

            is ServerSettingsScreenAction.SetPluginInteractAllowed ->
                mutations.pluginInteractPermission.mutateAsync(McpPluginPermissionParams(action.pluginId, action.allowed))

            is ServerSettingsScreenAction.SetPluginToolAllowed ->
                mutations.pluginToolPermission.mutateAsync(McpPluginToolPermissionParams(action.toolName, action.allowed))

            is ServerSettingsScreenAction.AddCertificate -> {
                // A newly generated certificate becomes the active one; the running TLS server
                // hot-swaps to it automatically.
                mutations.generateCertificate.mutateAsync(null)
            }

            is ServerSettingsScreenAction.SetActiveCertificate -> mutations.activateCertificate.mutateAsync(action.id)

            is ServerSettingsScreenAction.DeleteCertificate -> {
                mutations.deleteCertificate.mutateAsync(action.id)
                if (certificateDetailDialogEntry?.id == action.id) {
                    certificateDetailDialogEntry = null
                }
            }

            is ServerSettingsScreenAction.ShowCertificateDetail ->
                certificateDetailDialogEntry = certificates.find { it.id == action.id }

            is ServerSettingsScreenAction.DismissCertificateDetailDialog -> certificateDetailDialogEntry = null
        }
    }

    fun toUiState(mcpPermissionsSnapshot: McpPermissionsSnapshot): ServerSettingsScreenUiState {
        // The snippets must describe the endpoint an agent can actually reach right now, so they
        // follow the running server rather than the (possibly unapplied) port text field.
        val runningMcpStatus = mcpServerStatus as? McpServerStatus.Running
        val mcpEndpointUrl = "http://${runningMcpStatus?.host ?: "localhost"}:${runningMcpStatus?.port ?: savedMcpServerPort}/sse"
        return ServerSettingsScreenUiState(
            debugServerState = serverStatus.toServerState(),
            mcpServerState = mcpServerStatus.toServerState(),
            editingDebugPortText = debugPortText,
            editingWssPortText = wssPortText,
            editingWssEnabled = wssEnabled,
            // Reporting an error against values the user has not touched yet would flag a stored
            // configuration they cannot be in the middle of mistyping.
            debugServerSettingsError = debugServerSettingsError.takeIf { isDebugDirty },
            editingMcpPortText = mcpPortText,
            mcpClaudeCodeCommand = "claude mcp add --transport sse jetwhale $mcpEndpointUrl",
            mcpJsonConfig = mcpJsonConfig(mcpEndpointUrl),
            mcpPermissions = mcpPermissionsSnapshot.toMcpPermissionsUiState(),
            isDebugApplyVisible = isDebugDirty || isDebugStartFailed,
            isMcpApplyVisible = isMcpDirty || isMcpStartFailed,
            isDebugApplyEnabled = editedDebugServerSettings != null && (isDebugDirty || isDebugStartFailed),
            isMcpApplyEnabled = isMcpPortValid && (isMcpDirty || isMcpStartFailed),
            isDebugRetry = isDebugStartFailed && !isDebugDirty,
            isMcpRetry = isMcpStartFailed && !isMcpDirty,
            showDebugApplyConfirmDialog = showDebugApplyConfirmDialog,
            showMcpApplyConfirmDialog = showMcpApplyConfirmDialog,
            certificates = certificates,
            certificateDetailDialogEntry = certificateDetailDialogEntry,
        )
    }
}

/** The mutations the Debug Server page drives, held together so the action handler takes one object. */
@Stable
private class ServerSettingsMutations(
    val debugServerSettings: MutationObject<Unit, DebugServerSettings>,
    val mcpPort: MutationObject<Unit, Int>,
    val generateCertificate: MutationObject<SslCertificateEntry, String?>,
    val activateCertificate: MutationObject<Boolean, String>,
    val deleteCertificate: MutationObject<Boolean, String>,
    val hostGroupPermission: MutationObject<Unit, McpHostGroupPermissionParams>,
    val pluginInspectPermission: MutationObject<Unit, McpPluginPermissionParams>,
    val pluginInteractPermission: MutationObject<Unit, McpPluginPermissionParams>,
    val pluginToolPermission: MutationObject<Unit, McpPluginToolPermissionParams>,
)

@Composable
context(presenterContext: SettingsPresenterContext)
private fun rememberServerSettingsMutations(): ServerSettingsMutations = ServerSettingsMutations(
    debugServerSettings = rememberMutation(presenterContext.debugServerSettingsMutationKey),
    mcpPort = rememberMutation(presenterContext.mcpServerPortMutationKey),
    generateCertificate = rememberMutation(presenterContext.generateSslCertificateMutationKey),
    activateCertificate = rememberMutation(presenterContext.activateSslCertificateMutationKey),
    deleteCertificate = rememberMutation(presenterContext.deleteSslCertificateMutationKey),
    hostGroupPermission = rememberMutation(presenterContext.mcpHostGroupPermissionMutationKey),
    pluginInspectPermission = rememberMutation(presenterContext.mcpPluginInspectPermissionMutationKey),
    pluginInteractPermission = rememberMutation(presenterContext.mcpPluginInteractPermissionMutationKey),
    pluginToolPermission = rememberMutation(presenterContext.mcpPluginToolPermissionMutationKey),
)

private fun DebugWebSocketServerStatus.toServerState(): ServerState = when (this) {
    is DebugWebSocketServerStatus.Stopped -> ServerState.Stopped
    is DebugWebSocketServerStatus.Starting -> ServerState.Starting
    is DebugWebSocketServerStatus.Started -> ServerState.Running(host = host, port = port, wssPort = wssPort)
    is DebugWebSocketServerStatus.Error -> ServerState.Error(reason = message)
    is DebugWebSocketServerStatus.Stopping -> ServerState.Stopping
}

private fun McpServerStatus.toServerState(): ServerState = when (this) {
    is McpServerStatus.Stopped -> ServerState.Stopped
    is McpServerStatus.Starting -> ServerState.Starting
    is McpServerStatus.Running -> ServerState.Running(host = host, port = port)
    is McpServerStatus.Error -> ServerState.Error(reason = message)
    is McpServerStatus.Stopping -> ServerState.Stopping
}

private fun McpPermissionsSnapshot.toMcpPermissionsUiState(): McpPermissionsUiState = McpPermissionsUiState(
    allowedHostGroups = permissions.allowedHostGroups,
    plugins = plugins.map { plugin ->
        McpPluginPermissionUiState(
            pluginId = plugin.pluginId,
            displayName = plugin.displayName,
            inspectAllowed = plugin.pluginId !in permissions.pluginsDeniedInspect,
            interactAllowed = plugin.pluginId !in permissions.pluginsDeniedInteract,
            tools = plugin.tools.map { tool ->
                McpPluginToolUiState(
                    toolName = tool.name,
                    allowed = tool.name !in permissions.deniedPluginTools,
                )
            },
        )
    },
    isOverriddenForLaunch = isOverriddenForLaunch,
)

private fun mcpJsonConfig(endpointUrl: String): String = """
    {
      "mcpServers": {
        "jetwhale": {
          "type": "sse",
          "url": "$endpointUrl"
        }
      }
    }
""".trimIndent()
