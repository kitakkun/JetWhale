package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.OfficialPlugin
import com.kitakkun.jetwhale.host.model.PluginInstallJob
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.model.PluginInstallRequest
import com.kitakkun.jetwhale.host.model.PluginInstallStatus
import com.kitakkun.jetwhale.host.model.isActive
import com.kitakkun.jetwhale.host.model.isCancellable
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.add_plugin_from_file
import com.kitakkun.jetwhale.host.settings.adding_plugin_from_file
import com.kitakkun.jetwhale.host.settings.install_action_cancel
import com.kitakkun.jetwhale.host.settings.install_action_dismiss
import com.kitakkun.jetwhale.host.settings.install_action_retry
import com.kitakkun.jetwhale.host.settings.install_from_maven
import com.kitakkun.jetwhale.host.settings.install_progress_downloading_dependencies
import com.kitakkun.jetwhale.host.settings.install_progress_downloading_plugin
import com.kitakkun.jetwhale.host.settings.install_progress_loading_plugin
import com.kitakkun.jetwhale.host.settings.install_status_failed
import com.kitakkun.jetwhale.host.settings.install_status_installed
import com.kitakkun.jetwhale.host.settings.install_status_installing
import com.kitakkun.jetwhale.host.settings.install_status_queued
import com.kitakkun.jetwhale.host.settings.maven_install_install
import com.kitakkun.jetwhale.host.settings.official_plugin_installed
import com.kitakkun.jetwhale.host.settings.official_plugins
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwProgressIndicator
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.addPluginsPageItems(
    uiState: PluginSettingsScreenUiState,
    onClickAddPlugin: () -> Unit,
    onClickInstallFromMaven: () -> Unit,
    onClickInstallOfficialPlugin: (OfficialPlugin) -> Unit,
    onCancelInstall: (jobId: String) -> Unit,
    onRetryInstall: (PluginInstallRequest) -> Unit,
    onDismissInstall: (jobId: String) -> Unit,
) {
    item(key = "add_actions") {
        AddPluginActionsRow(
            addFromFileEnabled = !uiState.isAddingFromFile,
            onClickAddPlugin = onClickAddPlugin,
            onClickInstallFromMaven = onClickInstallFromMaven,
        )
    }
    installActivityItems(
        uiState = uiState,
        onCancelInstall = onCancelInstall,
        onRetryInstall = onRetryInstall,
        onDismissInstall = onDismissInstall,
    )
    if (uiState.officialPlugins.isNotEmpty()) {
        item(key = "official_header") {
            JwText(
                text = stringResource(Res.string.official_plugins),
                style = JwTheme.textStyles.title,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(
            items = uiState.officialPlugins,
            key = { official -> "official:${official.plugin.pluginId}" },
        ) { officialPlugin ->
            OfficialPluginRow(
                uiState = officialPlugin,
                onClickInstall = { onClickInstallOfficialPlugin(officialPlugin.plugin) },
            )
        }
    }
}

/** A Maven install is always accepted: it joins the install queue rather than competing with it. */
@Composable
private fun AddPluginActionsRow(
    addFromFileEnabled: Boolean,
    onClickAddPlugin: () -> Unit,
    onClickInstallFromMaven: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        JwButton(
            text = stringResource(Res.string.add_plugin_from_file),
            onClick = onClickAddPlugin,
            enabled = addFromFileEnabled,
            style = JwButtonStyle.Text,
        )
        JwButton(
            text = stringResource(Res.string.install_from_maven),
            onClick = onClickInstallFromMaven,
            style = JwButtonStyle.Text,
        )
    }
}

/**
 * Installs in flight or just finished, beside the actions that start them: an install only starts
 * from this page, so its failure has no business appearing over the installed list or the security
 * settings.
 */
private fun LazyListScope.installActivityItems(
    uiState: PluginSettingsScreenUiState,
    onCancelInstall: (jobId: String) -> Unit,
    onRetryInstall: (PluginInstallRequest) -> Unit,
    onDismissInstall: (jobId: String) -> Unit,
) {
    if (uiState.isAddingFromFile) {
        item(key = "add_from_file_progress") { AddingFromFileRow() }
    }
    uiState.addFromFileError?.let { error ->
        item(key = "add_from_file_error") { InstallErrorBanner(error = error) }
    }
    items(
        items = uiState.installJobs,
        key = { job -> "install:${job.id}" },
    ) { job ->
        InstallJobRow(
            job = job,
            onCancel = { onCancelInstall(job.id) },
            onRetry = { onRetryInstall(job.request) },
            onDismiss = { onDismissInstall(job.id) },
        )
    }
}

@Composable
private fun AddingFromFileRow(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        JwProgressIndicator()
        JwText(
            text = stringResource(Res.string.adding_plugin_from_file),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun InstallErrorBanner(
    error: String,
    modifier: Modifier = Modifier,
) {
    JwText(
        text = error,
        color = JwTheme.colors.onErrorContainer,
        style = JwTheme.textStyles.bodySmall,
        modifier = modifier
            .fillMaxWidth()
            .background(color = JwTheme.colors.errorContainer, shape = JwShapes.small)
            .padding(12.dp),
    )
}

/**
 * One queued, running or finished install. It keeps running when this screen closes, and reopening
 * the screen shows it where it is.
 */
@Composable
private fun InstallJobRow(
    job: PluginInstallJob,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = job.status
    JwPanel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PluginRowPadding,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (status.isActive) JwProgressIndicator()
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                JwText(
                    text = job.request.displayName,
                    style = JwTheme.textStyles.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                JwText(
                    text = installStatusText(status),
                    style = JwTheme.textStyles.bodySmall,
                    color = if (status is PluginInstallStatus.Failed) JwTheme.colors.error else JwTheme.colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status.isCancellable) {
                JwButton(text = stringResource(Res.string.install_action_cancel), onClick = onCancel, style = JwButtonStyle.Text)
            }
            if (status is PluginInstallStatus.Failed) {
                JwButton(text = stringResource(Res.string.install_action_retry), onClick = onRetry, style = JwButtonStyle.Secondary)
            }
            if (!status.isActive) {
                JwButton(text = stringResource(Res.string.install_action_dismiss), onClick = onDismiss, style = JwButtonStyle.Text)
            }
        }
    }
}

@Composable
private fun installStatusText(status: PluginInstallStatus): String = when (status) {
    is PluginInstallStatus.Queued -> stringResource(Res.string.install_status_queued)
    is PluginInstallStatus.Running -> status.progress?.let { installProgressText(it) } ?: stringResource(Res.string.install_status_installing)
    is PluginInstallStatus.Succeeded -> stringResource(Res.string.install_status_installed)
    is PluginInstallStatus.Failed -> stringResource(Res.string.install_status_failed, status.reason)
}

@Composable
private fun installProgressText(progress: PluginInstallProgress): String = when (progress) {
    is PluginInstallProgress.DownloadingPlugin ->
        stringResource(Res.string.install_progress_downloading_plugin)

    is PluginInstallProgress.DownloadingDependencies ->
        stringResource(
            Res.string.install_progress_downloading_dependencies,
            progress.completed + 1,
            progress.total,
        )

    is PluginInstallProgress.LoadingPlugin ->
        stringResource(Res.string.install_progress_loading_plugin)
}

@Composable
private fun OfficialPluginRow(
    uiState: OfficialPluginUiState,
    onClickInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwPanel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PluginRowPadding,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                JwText(
                    text = uiState.plugin.displayName,
                    style = JwTheme.textStyles.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                JwText(
                    text = uiState.plugin.description,
                    style = JwTheme.textStyles.bodySmall,
                    color = JwTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (uiState.isInstalled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JwIcon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = JwTheme.colors.accent,
                    )
                    JwText(
                        text = stringResource(Res.string.official_plugin_installed),
                        style = JwTheme.textStyles.label,
                        color = JwTheme.colors.accent,
                        maxLines = 1,
                    )
                }
            } else if (uiState.installJob?.status?.isActive == true) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JwProgressIndicator()
                    JwText(
                        text = stringResource(Res.string.install_status_installing),
                        style = JwTheme.textStyles.label,
                        color = JwTheme.colors.textSecondary,
                        maxLines = 1,
                    )
                }
            } else {
                JwButton(
                    text = stringResource(Res.string.maven_install_install),
                    onClick = onClickInstall,
                    style = JwButtonStyle.Secondary,
                )
            }
        }
    }
}
