package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.host.settings.component.SettingOptionView
import com.kitakkun.jetwhale.host.settings.component.TextFieldSettingsItemView
import com.kitakkun.jetwhale.host.settings.debug_server_label
import com.kitakkun.jetwhale.host.settings.debug_server_port_apply_confirm_message
import com.kitakkun.jetwhale.host.settings.debug_server_port_apply_confirm_title
import com.kitakkun.jetwhale.host.settings.debug_server_port_label
import com.kitakkun.jetwhale.host.settings.dialog_cancel
import com.kitakkun.jetwhale.host.settings.dialog_ok
import com.kitakkun.jetwhale.host.settings.mcp_server_label
import com.kitakkun.jetwhale.host.settings.mcp_server_port_apply_confirm_message
import com.kitakkun.jetwhale.host.settings.mcp_server_port_apply_confirm_title
import com.kitakkun.jetwhale.host.settings.mcp_server_port_label
import com.kitakkun.jetwhale.host.settings.server_configuration
import com.kitakkun.jetwhale.host.settings.server_port_apply
import com.kitakkun.jetwhale.host.settings.server_status_error
import com.kitakkun.jetwhale.host.settings.server_status_running
import com.kitakkun.jetwhale.host.settings.server_status_running_with_wss
import com.kitakkun.jetwhale.host.settings.server_status_starting
import com.kitakkun.jetwhale.host.settings.server_status_stopped
import com.kitakkun.jetwhale.host.settings.server_status_stopping
import com.kitakkun.jetwhale.host.settings.ssl_certificate
import com.kitakkun.jetwhale.host.settings.ssl_certificate_active
import com.kitakkun.jetwhale.host.settings.ssl_certificate_add
import com.kitakkun.jetwhale.host.settings.ssl_certificate_copy
import com.kitakkun.jetwhale.host.settings.ssl_certificate_created_at
import com.kitakkun.jetwhale.host.settings.ssl_certificate_delete
import com.kitakkun.jetwhale.host.settings.ssl_certificate_detail_title
import com.kitakkun.jetwhale.host.settings.ssl_certificate_no_certificate
import com.kitakkun.jetwhale.host.settings.ssl_certificate_set_active
import com.kitakkun.jetwhale.host.settings.ssl_certificate_show_detail
import org.jetbrains.compose.resources.stringResource

@Composable
fun ServerSettingsScreen(
    uiState: ServerSettingsScreenUiState,
    onDebugPortTextChange: (String) -> Unit,
    onApplyDebugPortChange: () -> Unit,
    onConfirmApplyDebugPortChange: () -> Unit,
    onDismissApplyDebugPortDialog: () -> Unit,
    onMcpPortTextChange: (String) -> Unit,
    onApplyMcpPortChange: () -> Unit,
    onConfirmApplyMcpPortChange: () -> Unit,
    onDismissApplyMcpPortDialog: () -> Unit,
    onAddCertificate: () -> Unit,
    onSetActiveCertificate: (String) -> Unit,
    onDeleteCertificate: (String) -> Unit,
    onShowCertificateDetail: (String) -> Unit,
    onDismissCertificateDetailDialog: () -> Unit,
) {
    if (uiState.showDebugApplyConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissApplyDebugPortDialog,
            title = { Text(stringResource(Res.string.debug_server_port_apply_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.debug_server_port_apply_confirm_message,
                        uiState.editingDebugPortText,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = onConfirmApplyDebugPortChange) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            },
            dismissButton = {
                Button(onClick = onDismissApplyDebugPortDialog) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            },
        )
    }

    if (uiState.showMcpApplyConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissApplyMcpPortDialog,
            title = { Text(stringResource(Res.string.mcp_server_port_apply_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.mcp_server_port_apply_confirm_message,
                        uiState.editingMcpPortText,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = onConfirmApplyMcpPortChange) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            },
            dismissButton = {
                Button(onClick = onDismissApplyMcpPortDialog) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            },
        )
    }

    uiState.certificateDetailDialogEntry?.let { entry ->
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = onDismissCertificateDetailDialog,
            title = { Text(stringResource(Res.string.ssl_certificate_detail_title)) },
            text = {
                Text(
                    text = entry.caCertificatePem,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                Button(
                    onClick = { clipboardManager.setText(AnnotatedString(entry.caCertificatePem)) },
                ) {
                    Text(stringResource(Res.string.ssl_certificate_copy))
                }
            },
            dismissButton = {
                Button(onClick = onDismissCertificateDetailDialog) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = SettingsScreenScaffoldPageContentPadding,
    ) {
        item {
            SettingOptionView(
                label = stringResource(Res.string.debug_server_label),
            ) {
                Text(
                    text = serverStateText(uiState.debugServerState),
                )
                TextFieldSettingsItemView(
                    label = stringResource(Res.string.debug_server_port_label),
                    text = uiState.editingDebugPortText,
                    onTextChange = onDebugPortTextChange,
                )
                if (uiState.isDebugApplyVisible) {
                    Button(
                        onClick = onApplyDebugPortChange,
                        enabled = uiState.isDebugApplyEnabled,
                    ) {
                        Text(stringResource(Res.string.server_port_apply))
                    }
                }
            }
        }
        item {
            SettingOptionView(
                label = stringResource(Res.string.mcp_server_label),
            ) {
                Text(
                    text = serverStateText(uiState.mcpServerState),
                )
                TextFieldSettingsItemView(
                    label = stringResource(Res.string.mcp_server_port_label),
                    text = uiState.editingMcpPortText,
                    onTextChange = onMcpPortTextChange,
                )
                if (uiState.isMcpApplyVisible) {
                    Button(
                        onClick = onApplyMcpPortChange,
                        enabled = uiState.isMcpApplyEnabled,
                    ) {
                        Text(stringResource(Res.string.server_port_apply))
                    }
                }
            }
        }
        item {
            SettingOptionView(
                label = stringResource(Res.string.ssl_certificate),
            ) {
                if (uiState.certificates.isEmpty()) {
                    Text(stringResource(Res.string.ssl_certificate_no_certificate))
                }
                uiState.certificates.forEach { certificate ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = buildString {
                                append(certificate.name)
                                if (certificate.isActive) append(" (${stringResource(Res.string.ssl_certificate_active)})")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(Res.string.ssl_certificate_created_at, certificate.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!certificate.isActive) {
                            TextButton(onClick = { onSetActiveCertificate(certificate.id) }) {
                                Text(stringResource(Res.string.ssl_certificate_set_active))
                            }
                        }
                        TextButton(onClick = { onShowCertificateDetail(certificate.id) }) {
                            Text(stringResource(Res.string.ssl_certificate_show_detail))
                        }
                        TextButton(onClick = { onDeleteCertificate(certificate.id) }) {
                            Text(stringResource(Res.string.ssl_certificate_delete))
                        }
                    }
                }
                OutlinedButton(onClick = onAddCertificate) {
                    Text(stringResource(Res.string.ssl_certificate_add))
                }
            }
        }
    }
}

@Composable
private fun serverStateText(state: ServerState): String = when (state) {
    is ServerState.Starting -> stringResource(Res.string.server_status_starting)

    is ServerState.Running -> state.wssPort?.let { wssPort ->
        stringResource(Res.string.server_status_running_with_wss, state.port, wssPort)
    } ?: stringResource(Res.string.server_status_running, state.port)

    is ServerState.Error -> stringResource(Res.string.server_status_error, state.reason)

    is ServerState.Stopping -> stringResource(Res.string.server_status_stopping)

    is ServerState.Stopped -> stringResource(Res.string.server_status_stopped)
}
