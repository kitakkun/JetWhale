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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.awt.datatransfer.StringSelection

/** The tallest a certificate's PEM text grows inside its dialog before it scrolls. */
private val CertificateTextMaxHeight = 320.dp

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
    modifier: Modifier = Modifier,
) {
    if (uiState.showDebugApplyConfirmDialog) {
        DebugServerApplyConfirmDialog(
            editingWssEnabled = uiState.editingWssEnabled,
            editingDebugPortText = uiState.editingDebugPortText,
            editingWssPortText = uiState.editingWssPortText,
            onConfirm = onConfirmApplyDebugServerSettingsChange,
            onDismiss = onDismissApplyDebugServerSettingsDialog,
        )
    }

    if (uiState.showMcpApplyConfirmDialog) {
        McpPortApplyConfirmDialog(
            editingMcpPortText = uiState.editingMcpPortText,
            onConfirm = onConfirmApplyMcpPortChange,
            onDismiss = onDismissApplyMcpPortDialog,
        )
    }

    uiState.certificateDetailDialogEntry?.let { entry ->
        CertificateDetailDialog(
            caCertificatePem = entry.caCertificatePem,
            onDismiss = onDismissCertificateDetailDialog,
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = SettingsScreenScaffoldPageContentPadding,
    ) {
        if (page == SettingsScreenPage.DebugServer) {
            item {
                DebugServerSection(
                    serverState = uiState.debugServerState,
                    editingDebugPortText = uiState.editingDebugPortText,
                    editingWssPortText = uiState.editingWssPortText,
                    editingWssEnabled = uiState.editingWssEnabled,
                    settingsError = uiState.debugServerSettingsError,
                    isApplyVisible = uiState.isDebugApplyVisible,
                    isApplyEnabled = uiState.isDebugApplyEnabled,
                    isRetry = uiState.isDebugRetry,
                    onDebugPortTextChange = onDebugPortTextChange,
                    onWssPortTextChange = onWssPortTextChange,
                    onWssEnabledChange = onWssEnabledChange,
                    onApplyDebugServerSettingsChange = onApplyDebugServerSettingsChange,
                )
            }
        }
        if (page == SettingsScreenPage.McpServer) {
            item {
                McpServerSection(
                    serverState = uiState.mcpServerState,
                    editingMcpPortText = uiState.editingMcpPortText,
                    claudeCodeCommand = uiState.mcpClaudeCodeCommand,
                    jsonConfig = uiState.mcpJsonConfig,
                    isApplyVisible = uiState.isMcpApplyVisible,
                    isApplyEnabled = uiState.isMcpApplyEnabled,
                    isRetry = uiState.isMcpRetry,
                    onMcpPortTextChange = onMcpPortTextChange,
                    onApplyMcpPortChange = onApplyMcpPortChange,
                    onClickOpenMcpGuide = onClickOpenMcpGuide,
                )
            }
        }
        if (page == SettingsScreenPage.McpPermissions) {
            item {
                SettingOptionView(label = stringResource(Res.string.mcp_permission_title)) {
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
                SslCertificateSection(
                    certificates = uiState.certificates,
                    onAddCertificate = onAddCertificate,
                    onSetActiveCertificate = onSetActiveCertificate,
                    onDeleteCertificate = onDeleteCertificate,
                    onShowCertificateDetail = onShowCertificateDetail,
                )
            }
        }
    }
}

@Composable
private fun DebugServerApplyConfirmDialog(
    editingWssEnabled: Boolean,
    editingDebugPortText: String,
    editingWssPortText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwDialog(
        onDismissRequest = onDismiss,
        closeLabel = stringResource(Res.string.close),
        title = stringResource(Res.string.debug_server_port_apply_confirm_title),
        modifier = modifier,
        confirmButton = {
            JwButton(
                text = stringResource(Res.string.dialog_ok),
                onClick = onConfirm,
                style = JwButtonStyle.Primary,
            )
        },
        dismissButton = {
            JwButton(
                text = stringResource(Res.string.dialog_cancel),
                onClick = onDismiss,
                style = JwButtonStyle.Text,
            )
        },
    ) {
        JwText(
            if (editingWssEnabled) {
                stringResource(
                    Res.string.debug_server_port_apply_confirm_message_with_wss,
                    editingDebugPortText,
                    editingWssPortText,
                )
            } else {
                stringResource(
                    Res.string.debug_server_port_apply_confirm_message,
                    editingDebugPortText,
                )
            },
        )
    }
}

@Composable
private fun McpPortApplyConfirmDialog(
    editingMcpPortText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwDialog(
        onDismissRequest = onDismiss,
        closeLabel = stringResource(Res.string.close),
        title = stringResource(Res.string.mcp_server_port_apply_confirm_title),
        modifier = modifier,
        confirmButton = {
            JwButton(
                text = stringResource(Res.string.dialog_ok),
                onClick = onConfirm,
                style = JwButtonStyle.Primary,
            )
        },
        dismissButton = {
            JwButton(
                text = stringResource(Res.string.dialog_cancel),
                onClick = onDismiss,
                style = JwButtonStyle.Text,
            )
        },
    ) {
        JwText(stringResource(Res.string.mcp_server_port_apply_confirm_message, editingMcpPortText))
    }
}

@Composable
private fun CertificateDetailDialog(
    caCertificatePem: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    JwDialog(
        onDismissRequest = onDismiss,
        closeLabel = stringResource(Res.string.close),
        title = stringResource(Res.string.ssl_certificate_detail_title),
        modifier = modifier,
        confirmButton = {
            JwButton(
                text = stringResource(Res.string.ssl_certificate_copy),
                onClick = { scope.launch { clipboard.setPlainText(caCertificatePem) } },
                style = JwButtonStyle.Primary,
            )
        },
        dismissButton = {
            JwButton(
                text = stringResource(Res.string.dialog_ok),
                onClick = onDismiss,
                style = JwButtonStyle.Text,
            )
        },
    ) {
        JwText(
            text = caCertificatePem,
            style = JwTheme.textStyles.bodySmall,
            modifier = Modifier
                .heightIn(max = CertificateTextMaxHeight)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun DebugServerSection(
    serverState: ServerState,
    editingDebugPortText: String,
    editingWssPortText: String,
    editingWssEnabled: Boolean,
    settingsError: DebugServerSettingsError?,
    isApplyVisible: Boolean,
    isApplyEnabled: Boolean,
    isRetry: Boolean,
    onDebugPortTextChange: (String) -> Unit,
    onWssPortTextChange: (String) -> Unit,
    onWssEnabledChange: (Boolean) -> Unit,
    onApplyDebugServerSettingsChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingOptionView(
        label = stringResource(Res.string.debug_server_label),
        modifier = modifier,
    ) {
        JwText(text = serverStateText(serverState))
        TextFieldSettingsItemView(
            label = stringResource(Res.string.debug_server_port_label),
            text = editingDebugPortText,
            onTextChange = onDebugPortTextChange,
        )
        SwitchSettingsItemView(
            label = stringResource(Res.string.wss_enabled_label),
            isChecked = editingWssEnabled,
            onCheckedChange = onWssEnabledChange,
        )
        TextFieldSettingsItemView(
            label = stringResource(Res.string.wss_port_label),
            text = editingWssPortText,
            onTextChange = onWssPortTextChange,
        )
        settingsError?.let { error ->
            JwText(
                text = debugServerSettingsErrorText(error),
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.error,
            )
        }
        if (isApplyVisible) {
            JwButton(
                text = applyButtonText(isRetry = isRetry),
                onClick = onApplyDebugServerSettingsChange,
                enabled = isApplyEnabled,
                style = JwButtonStyle.Primary,
            )
        }
    }
}

@Composable
private fun McpServerSection(
    serverState: ServerState,
    editingMcpPortText: String,
    claudeCodeCommand: String,
    jsonConfig: String,
    isApplyVisible: Boolean,
    isApplyEnabled: Boolean,
    isRetry: Boolean,
    onMcpPortTextChange: (String) -> Unit,
    onApplyMcpPortChange: () -> Unit,
    onClickOpenMcpGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingOptionView(
        label = stringResource(Res.string.mcp_server_label),
        modifier = modifier,
    ) {
        JwText(text = serverStateText(serverState))
        TextFieldSettingsItemView(
            label = stringResource(Res.string.mcp_server_port_label),
            text = editingMcpPortText,
            onTextChange = onMcpPortTextChange,
        )
        if (isApplyVisible) {
            JwButton(
                text = applyButtonText(isRetry = isRetry),
                onClick = onApplyMcpPortChange,
                enabled = isApplyEnabled,
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
            snippet = claudeCodeCommand,
        )
        McpSnippetView(
            label = stringResource(Res.string.mcp_setup_json_label),
            snippet = jsonConfig,
        )
        JwButton(
            text = stringResource(Res.string.mcp_setup_open_guide),
            onClick = onClickOpenMcpGuide,
            style = JwButtonStyle.Secondary,
        )
    }
}

@Composable
private fun SslCertificateSection(
    certificates: List<CertificateUiEntry>,
    onAddCertificate: () -> Unit,
    onSetActiveCertificate: (String) -> Unit,
    onDeleteCertificate: (String) -> Unit,
    onShowCertificateDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingOptionView(
        label = stringResource(Res.string.ssl_certificate),
        modifier = modifier,
    ) {
        JwText(
            text = stringResource(Res.string.ssl_certificate_apply_note),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
        if (certificates.isEmpty()) {
            JwText(stringResource(Res.string.ssl_certificate_no_certificate))
        }
        certificates.forEach { certificate ->
            CertificateRow(
                certificate = certificate,
                onSetActiveCertificate = onSetActiveCertificate,
                onDeleteCertificate = onDeleteCertificate,
                onShowCertificateDetail = onShowCertificateDetail,
            )
        }
        JwButton(
            text = stringResource(Res.string.ssl_certificate_add),
            onClick = onAddCertificate,
            style = JwButtonStyle.Secondary,
        )
    }
}

@Composable
private fun CertificateRow(
    certificate: CertificateUiEntry,
    onSetActiveCertificate: (String) -> Unit,
    onDeleteCertificate: (String) -> Unit,
    onShowCertificateDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
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

@Composable
private fun McpSnippetView(
    label: String,
    snippet: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = modifier,
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
            onClick = { scope.launch { clipboard.setPlainText(snippet) } },
            style = JwButtonStyle.Text,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(StringSelection(text)))
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

@Preview
@Composable
private fun ServerSettingsScreenPreview() {
    JwTheme(darkTheme = false) {
        ServerSettingsScreen(
            page = SettingsScreenPage.DebugServer,
            uiState = ServerSettingsScreenUiState(
                debugServerState = ServerState.Running(host = "localhost", port = 5080, wssPort = 5443),
                mcpServerState = ServerState.Stopped,
                editingDebugPortText = "5080",
                editingWssPortText = "5443",
                editingWssEnabled = true,
                debugServerSettingsError = null,
                editingMcpPortText = "7080",
                mcpClaudeCodeCommand = "claude mcp add --transport sse jetwhale http://localhost:7080/sse",
                mcpJsonConfig = "{}",
                mcpPermissions = McpPermissionsUiState(
                    allowedHostGroups = setOf(McpHostToolGroup.OBSERVE),
                    plugins = emptyList(),
                    isOverriddenForLaunch = false,
                ),
                isDebugApplyVisible = true,
                isMcpApplyVisible = false,
                isDebugApplyEnabled = true,
                isMcpApplyEnabled = false,
                isDebugRetry = false,
                isMcpRetry = false,
                showDebugApplyConfirmDialog = false,
                showMcpApplyConfirmDialog = false,
                certificates = emptyList(),
                certificateDetailDialogEntry = null,
            ),
            onDebugPortTextChange = {},
            onWssPortTextChange = {},
            onWssEnabledChange = {},
            onApplyDebugServerSettingsChange = {},
            onConfirmApplyDebugServerSettingsChange = {},
            onDismissApplyDebugServerSettingsDialog = {},
            onMcpPortTextChange = {},
            onApplyMcpPortChange = {},
            onConfirmApplyMcpPortChange = {},
            onDismissApplyMcpPortDialog = {},
            onClickOpenMcpGuide = {},
            onSetHostGroupAllowed = { _, _ -> },
            onSetPluginInspectAllowed = { _, _ -> },
            onSetPluginInteractAllowed = { _, _ -> },
            onSetPluginToolAllowed = { _, _ -> },
            onAddCertificate = {},
            onSetActiveCertificate = {},
            onDeleteCertificate = {},
            onShowCertificateDetail = {},
            onDismissCertificateDetailDialog = {},
        )
    }
}
