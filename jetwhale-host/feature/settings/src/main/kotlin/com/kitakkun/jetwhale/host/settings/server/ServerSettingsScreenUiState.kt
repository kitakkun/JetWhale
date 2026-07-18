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
    val certificates: List<CertificateUiEntry>,
    val certificateDetailDialogEntry: CertificateUiEntry?,
    val showRestartRequiredDialog: Boolean,
)

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
