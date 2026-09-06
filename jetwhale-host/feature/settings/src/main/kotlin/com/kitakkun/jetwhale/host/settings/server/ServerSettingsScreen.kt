package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.host.settings.close
import com.kitakkun.jetwhale.host.settings.component.SettingOptionView
import com.kitakkun.jetwhale.host.settings.component.SwitchSettingsItemView
import com.kitakkun.jetwhale.host.settings.component.TextFieldSettingsItemView
import com.kitakkun.jetwhale.host.settings.copy_to_clipboard
import com.kitakkun.jetwhale.host.settings.debug_server_label
import com.kitakkun.jetwhale.host.settings.debug_server_port_apply_confirm_message
import com.kitakkun.jetwhale.host.settings.debug_server_port_apply_confirm_message_with_wss
import com.kitakkun.jetwhale.host.settings.debug_server_port_apply_confirm_title
import com.kitakkun.jetwhale.host.settings.debug_server_port_conflict_error
import com.kitakkun.jetwhale.host.settings.debug_server_port_invalid_error
import com.kitakkun.jetwhale.host.settings.debug_server_port_label
import com.kitakkun.jetwhale.host.settings.dialog_cancel
import com.kitakkun.jetwhale.host.settings.dialog_ok
import com.kitakkun.jetwhale.host.settings.mcp_permission_title
import com.kitakkun.jetwhale.host.settings.mcp_server_label
import com.kitakkun.jetwhale.host.settings.mcp_server_port_apply_confirm_message
import com.kitakkun.jetwhale.host.settings.mcp_server_port_apply_confirm_title
import com.kitakkun.jetwhale.host.settings.mcp_server_port_label
import com.kitakkun.jetwhale.host.settings.mcp_setup_claude_code_label
import com.kitakkun.jetwhale.host.settings.mcp_setup_json_label
import com.kitakkun.jetwhale.host.settings.mcp_setup_note
import com.kitakkun.jetwhale.host.settings.mcp_setup_open_guide
import com.kitakkun.jetwhale.host.settings.server_configuration
import com.kitakkun.jetwhale.host.settings.server_port_apply
import com.kitakkun.jetwhale.host.settings.server_start_retry
import com.kitakkun.jetwhale.host.settings.server_status_error
import com.kitakkun.jetwhale.host.settings.server_status_running
import com.kitakkun.jetwhale.host.settings.server_status_running_with_wss
import com.kitakkun.jetwhale.host.settings.server_status_starting
import com.kitakkun.jetwhale.host.settings.server_status_stopped
import com.kitakkun.jetwhale.host.settings.server_status_stopping
import com.kitakkun.jetwhale.host.settings.ssl_certificate
import com.kitakkun.jetwhale.host.settings.ssl_certificate_active
import com.kitakkun.jetwhale.host.settings.ssl_certificate_add
import com.kitakkun.jetwhale.host.settings.ssl_certificate_apply_note
import com.kitakkun.jetwhale.host.settings.ssl_certificate_copy
import com.kitakkun.jetwhale.host.settings.ssl_certificate_created_at
import com.kitakkun.jetwhale.host.settings.ssl_certificate_delete
import com.kitakkun.jetwhale.host.settings.ssl_certificate_detail_title
import com.kitakkun.jetwhale.host.settings.ssl_certificate_no_certificate
import com.kitakkun.jetwhale.host.settings.ssl_certificate_set_active
import com.kitakkun.jetwhale.host.settings.ssl_certificate_show_detail
import com.kitakkun.jetwhale.host.settings.wss_enabled_label
import com.kitakkun.jetwhale.host.settings.wss_port_label
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSurface
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun ServerSettingsScreen(
    page: SettingsScreenPage,
    uiState: ServerSettingsScreenUiState,
    onDebugPortTextChange: (String) -> Unit,
    onWssPortTextChange: (String) -> Unit,
    onWssEnabledChange: (Boolean) -> Unit,
    onApplyDebugServerSettingsChange: () -> Unit,
    onConfirmApplyDebugServerSettingsChange: () -> Unit,
    onDismissApplyDebugServerSettingsDialog: () -> Unit,
    onMcpPortTextChange: (String) -> Unit,
    onApplyMcpPortChange: () -> Unit,
    onConfirmApplyMcpPortChange: () -> Unit,
    onDismissApplyMcpPortDialog: () -> Unit,
    onClickOpenMcpGuide: () -> Unit,
    onSetHostGroupAllowed: (McpHostToolGroup, Boolean) -> Unit,
    onSetPluginInspectAllowed: (pluginId: String, allowed: Boolean) -> Unit,
    onSetPluginInteractAllowed: (pluginId: String, allowed: Boolean) -> Unit,
    onSetPluginToolAllowed: (toolName: String, allowed: Boolean) -> Unit,
    onAddCertificate: () -> Unit,
    onSetActiveCertificate: (String) -> Unit,
    onDeleteCertificate: (String) -> Unit,
    onShowCertificateDetail: (String) -> Unit,
    onDismissCertificateDetailDialog: () -> Unit,
) {
    if (uiState.showDebugApplyConfirmDialog) {
        JwDialog(
            onDismissRequest = onDismissApplyDebugServerSettingsDialog,
            closeLabel = stringResource(Res.string.close),
            title = stringResource(Res.string.debug_server_port_apply_confirm_title),
            text = {
                JwText(
                    if (uiState.editingWssEnabled) {
                        stringResource(
                            Res.string.debug_server_port_apply_confirm_message_with_wss,
                            uiState.editingDebugPortText,
                            uiState.editingWssPortText,
                        )
                    } else {
                        stringResource(
                            Res.string.debug_server_port_apply_confirm_message,
                            uiState.editingDebugPortText,
                        )
                    },
                )
            },
            confirmButton = {
                JwButton(
                    text = stringResource(Res.string.dialog_ok),
                    onClick = onConfirmApplyDebugServerSettingsChange,
                    style = JwButtonStyle.Primary,
                )
            },
            dismissButton = {
                JwButton(
                    text = stringResource(Res.string.dialog_cancel),
                    onClick = onDismissApplyDebugServerSettingsDialog,
                    style = JwButtonStyle.Text,
                )
            },
        )
    }

    if (uiState.showMcpApplyConfirmDialog) {
        JwDialog(
            onDismissRequest = onDismissApplyMcpPortDialog,
            closeLabel = stringResource(Res.string.close),
            title = stringResource(Res.string.mcp_server_port_apply_confirm_title),
            text = {
                JwText(
                    stringResource(
                        Res.string.mcp_server_port_apply_confirm_message,
                        uiState.editingMcpPortText,
                    ),
                )
            },
            confirmButton = {
                JwButton(
                    text = stringResource(Res.string.dialog_ok),
                    onClick = onConfirmApplyMcpPortChange,
                    style = JwButtonStyle.Primary,
                )
            },
            dismissButton = {
                JwButton(
                    text = stringResource(Res.string.dialog_cancel),
                    onClick = onDismissApplyMcpPortDialog,
                    style = JwButtonStyle.Text,
                )
            },
        )
    }

    uiState.certificateDetailDialogEntry?.let { entry ->
        val clipboardManager = LocalClipboardManager.current
        JwDialog(
            onDismissRequest = onDismissCertificateDetailDialog,
            closeLabel = stringResource(Res.string.close),
            title = stringResource(Res.string.ssl_certificate_detail_title),
            text = {
                JwText(
                    text = entry.caCertificatePem,
                    style = JwTheme.textStyles.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                JwButton(
                    text = stringResource(Res.string.ssl_certificate_copy),
                    onClick = { clipboardManager.setText(AnnotatedString(entry.caCertificatePem)) },
                    style = JwButtonStyle.Primary,
                )
            },
            dismissButton = {
                JwButton(
                    text = stringResource(Res.string.dialog_ok),
                    onClick = onDismissCertificateDetailDialog,
                    style = JwButtonStyle.Text,
                )
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = SettingsScreenScaffoldPageContentPadding,
    ) {
        if (page == SettingsScreenPage.DebugServer) {
            item {
                SettingOptionView(
                    label = stringResource(Res.string.debug_server_label),
                ) {
                    JwText(
                        text = serverStateText(uiState.debugServerState),
                    )
                    TextFieldSettingsItemView(
                        label = stringResource(Res.string.debug_server_port_label),
                        text = uiState.editingDebugPortText,
                        onTextChange = onDebugPortTextChange,
                    )
                    SwitchSettingsItemView(
                        label = stringResource(Res.string.wss_enabled_label),
                        isChecked = uiState.editingWssEnabled,
                        onCheckedChange = onWssEnabledChange,
                    )
                    TextFieldSettingsItemView(
                        label = stringResource(Res.string.wss_port_label),
                        text = uiState.editingWssPortText,
                        onTextChange = onWssPortTextChange,
                    )
                    uiState.debugServerSettingsError?.let { error ->
                        JwText(
                            text = debugServerSettingsErrorText(error),
                            style = JwTheme.textStyles.bodySmall,
                            color = JwTheme.colors.error,
                        )
                    }
                    if (uiState.isDebugApplyVisible) {
                        JwButton(
                            text = applyButtonText(isRetry = uiState.isDebugRetry),
                            onClick = onApplyDebugServerSettingsChange,
                            enabled = uiState.isDebugApplyEnabled,
                            style = JwButtonStyle.Primary,
                        )
                    }
                }
            }
        }
        if (page == SettingsScreenPage.McpServer) {
            item {
                SettingOptionView(
                    label = stringResource(Res.string.mcp_server_label),
                ) {
                    JwText(
                        text = serverStateText(uiState.mcpServerState),
                    )
                    TextFieldSettingsItemView(
                        label = stringResource(Res.string.mcp_server_port_label),
                        text = uiState.editingMcpPortText,
                        onTextChange = onMcpPortTextChange,
                    )
                    if (uiState.isMcpApplyVisible) {
                        JwButton(
                            text = applyButtonText(isRetry = uiState.isMcpRetry),
                            onClick = onApplyMcpPortChange,
                            enabled = uiState.isMcpApplyEnabled,
                            style = JwButtonStyle.Primary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    JwText(
                        text = stringResource(Res.string.mcp_setup_note),
                        style = JwTheme.textStyles.bodySmall,
                        color = JwTheme.colors.textSecondary,
                    )
                    McpSnippetView(
                        label = stringResource(Res.string.mcp_setup_claude_code_label),
                        snippet = uiState.mcpClaudeCodeCommand,
                    )
                    McpSnippetView(
                        label = stringResource(Res.string.mcp_setup_json_label),
                        snippet = uiState.mcpJsonConfig,
                    )
                    JwButton(
                        text = stringResource(Res.string.mcp_setup_open_guide),
                        onClick = onClickOpenMcpGuide,
                        style = JwButtonStyle.Secondary,
                    )
                }
            }
        }
        if (page == SettingsScreenPage.McpPermissions) {
            item {
                SettingOptionView(
                    label = stringResource(Res.string.mcp_permission_title),
                ) {
                    McpPermissionsTreeView(
                        uiState = uiState.mcpPermissions,
                        onSetHostGroupAllowed = onSetHostGroupAllowed,
                        onSetPluginInspectAllowed = onSetPluginInspectAllowed,
                        onSetPluginInteractAllowed = onSetPluginInteractAllowed,
                        onSetPluginToolAllowed = onSetPluginToolAllowed,
                    )
                }
            }
        }
        if (page == SettingsScreenPage.SslCertificate) {
            item {
                SettingOptionView(
                    label = stringResource(Res.string.ssl_certificate),
                ) {
                    JwText(
                        text = stringResource(Res.string.ssl_certificate_apply_note),
                        style = JwTheme.textStyles.bodySmall,
                        color = JwTheme.colors.textSecondary,
                    )
                    if (uiState.certificates.isEmpty()) {
                        JwText(stringResource(Res.string.ssl_certificate_no_certificate))
                    }
                    uiState.certificates.forEach { certificate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            JwText(
                                text = buildString {
                                    append(certificate.name)
                                    if (certificate.isActive) append(" (${stringResource(Res.string.ssl_certificate_active)})")
                                },
                                style = JwTheme.textStyles.body,
                                modifier = Modifier.weight(1f),
                            )
                            JwText(
                                text = stringResource(Res.string.ssl_certificate_created_at, certificate.createdAt),
                                style = JwTheme.textStyles.bodySmall,
                            )
                            if (!certificate.isActive) {
                                JwButton(
                                    text = stringResource(Res.string.ssl_certificate_set_active),
                                    onClick = { onSetActiveCertificate(certificate.id) },
                                    style = JwButtonStyle.Text,
                                )
                            }
                            JwButton(
                                text = stringResource(Res.string.ssl_certificate_show_detail),
                                onClick = { onShowCertificateDetail(certificate.id) },
                                style = JwButtonStyle.Text,
                            )
                            JwButton(
                                text = stringResource(Res.string.ssl_certificate_delete),
                                onClick = { onDeleteCertificate(certificate.id) },
                                style = JwButtonStyle.Text,
                            )
                        }
                    }
                    JwButton(
                        text = stringResource(Res.string.ssl_certificate_add),
                        onClick = onAddCertificate,
                        style = JwButtonStyle.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun McpSnippetView(
    label: String,
    snippet: String,
) {
    val clipboardManager = LocalClipboardManager.current
    Spacer(Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            JwText(
                text = label,
                style = JwTheme.textStyles.label,
                color = JwTheme.colors.textSecondary,
            )
            JwSurface(
                color = JwTheme.colors.neutralContainer,
                shape = JwShapes.small,
            ) {
                SelectionContainer {
                    JwText(
                        text = snippet,
                        style = JwTheme.textStyles.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
        JwButton(
            text = stringResource(Res.string.copy_to_clipboard),
            onClick = { clipboardManager.setText(AnnotatedString(snippet)) },
            style = JwButtonStyle.Text,
        )
    }
}

@Composable
private fun debugServerSettingsErrorText(error: DebugServerSettingsError): String = when (error) {
    DebugServerSettingsError.InvalidPort -> stringResource(Res.string.debug_server_port_invalid_error)
    DebugServerSettingsError.PortConflict -> stringResource(Res.string.debug_server_port_conflict_error)
}

@Composable
private fun applyButtonText(isRetry: Boolean): String = if (isRetry) {
    stringResource(Res.string.server_start_retry)
} else {
    stringResource(Res.string.server_port_apply)
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
