package com.kitakkun.jetwhale.host.settings.server

data class ServerSettingsScreenUiState(
    val debugServerState: ServerState,
    val mcpServerState: ServerState,
    val editingDebugPortText: String,
    val editingWssPortText: String,
    val editingWssEnabled: Boolean,
    val debugServerSettingsError: DebugServerSettingsError?,
    val editingMcpPortText: String,
    val mcpClaudeCodeCommand: String,
    val mcpJsonConfig: String,
    val mcpPermissions: McpPermissionsUiState,
    val isDebugApplyVisible: Boolean,
    val isMcpApplyVisible: Boolean,
    val isDebugApplyEnabled: Boolean,
    val isMcpApplyEnabled: Boolean,
    val isDebugRetry: Boolean,
    val isMcpRetry: Boolean,
    val showDebugApplyConfirmDialog: Boolean,
    val showMcpApplyConfirmDialog: Boolean,
    val certificates: List<CertificateUiEntry>,
    val certificateDetailDialogEntry: CertificateUiEntry?,
)

/** Why the debug server settings cannot be applied as they currently stand. */
enum class DebugServerSettingsError {
    InvalidPort,

    /** Both connectors bind on the same host, so they cannot share a port. */
    PortConflict,
}

data class CertificateUiEntry(
    val id: String,
    val name: String,
    val createdAt: String,
    val caCertificatePem: String,
    val isActive: Boolean,
)

sealed interface ServerState {
    data object Stopped : ServerState
    data class Running(val host: String, val port: Int, val wssPort: Int? = null) : ServerState
    data class Error(val reason: String) : ServerState
    data object Starting : ServerState
    data object Stopping : ServerState
}
