package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.OfficialPlugin
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.host.settings.add_plugin_from_file
import com.kitakkun.jetwhale.host.settings.approve_untrusted_plugin
import com.kitakkun.jetwhale.host.settings.dialog_ok
import com.kitakkun.jetwhale.host.settings.failed_jar_path_hint
import com.kitakkun.jetwhale.host.settings.failed_to_load_plugins
import com.kitakkun.jetwhale.host.settings.install_from_maven
import com.kitakkun.jetwhale.host.settings.install_progress_downloading_dependencies
import com.kitakkun.jetwhale.host.settings.install_progress_downloading_plugin
import com.kitakkun.jetwhale.host.settings.install_progress_loading_plugin
import com.kitakkun.jetwhale.host.settings.installed_plugins
import com.kitakkun.jetwhale.host.settings.maven_install_install
import com.kitakkun.jetwhale.host.settings.official_plugin_installed
import com.kitakkun.jetwhale.host.settings.official_plugins
import com.kitakkun.jetwhale.host.settings.untrusted_jar_hint
import com.kitakkun.jetwhale.host.settings.untrusted_plugins
import org.jetbrains.compose.resources.stringResource

@Composable
fun PluginSettingsScreen(
    uiState: PluginSettingsScreenUiState,
    onClickAddPlugin: () -> Unit,
    onApproveUntrustedJar: (String) -> Unit,
    onClickInstallFromMaven: () -> Unit,
    onClickInstallOfficialPlugin: (OfficialPlugin) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFailedJarsDialog by remember { mutableStateOf(false) }

    if (showFailedJarsDialog) {
        AlertDialog(
            onDismissRequest = { showFailedJarsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(Res.string.failed_to_load_plugins)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.failed_jar_path_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    uiState.failedJarPaths.forEach { path ->
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFailedJarsDialog = false }) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SettingsScreenScaffoldPageContentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.installed_plugins),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 0.dp,
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ),
        ) {
            stickyHeader {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(top = 8.dp),
                ) {
                    Text("id", Modifier.weight(1f))
                    Text("name", Modifier.weight(1f))
                    Text("version", Modifier.width(100.dp))
                }
                HorizontalDivider()
            }
            items(
                items = uiState.plugins,
                key = { plugin -> plugin.id },
            ) { plugin ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = plugin.id, Modifier.weight(1f))
                    Text(text = plugin.name, Modifier.weight(1f))
                    Text(text = plugin.version, Modifier.width(100.dp))
                }
            }
        }
        if (uiState.failedJarPaths.isNotEmpty()) {
            TextButton(
                onClick = { showFailedJarsDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = stringResource(Res.string.failed_to_load_plugins) +
                        " (${uiState.failedJarPaths.size})",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (uiState.untrustedJarPaths.isNotEmpty()) {
            UntrustedPluginsSection(
                untrustedJarPaths = uiState.untrustedJarPaths,
                onApprove = onApproveUntrustedJar,
            )
        }
        if (uiState.officialPlugins.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.official_plugins),
                style = MaterialTheme.typography.titleMedium,
            )
            uiState.officialPlugins.forEach { officialPlugin ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = officialPlugin.plugin.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = officialPlugin.plugin.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (officialPlugin.isInstalled) {
                        Text(
                            text = stringResource(Res.string.official_plugin_installed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Button(
                            onClick = { onClickInstallOfficialPlugin(officialPlugin.plugin) },
                            enabled = !uiState.isInstalling,
                        ) {
                            Text(stringResource(Res.string.maven_install_install))
                        }
                    }
                }
            }
        }
        uiState.installError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(8.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onClickAddPlugin,
                enabled = !uiState.isInstalling,
            ) {
                Text(stringResource(Res.string.add_plugin_from_file))
            }
            Button(
                onClick = onClickInstallFromMaven,
                enabled = !uiState.isInstalling,
            ) {
                Text(stringResource(Res.string.install_from_maven))
            }
            if (uiState.isInstalling) {
                uiState.installProgress?.let { progress ->
                    Text(
                        text = when (progress) {
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
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun UntrustedPluginsSection(
    untrustedJarPaths: List<String>,
    onApprove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(Res.string.untrusted_plugins) + " (${untrustedJarPaths.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Text(
            text = stringResource(Res.string.untrusted_jar_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        untrustedJarPaths.forEach { path ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onApprove(path) }) {
                    Text(stringResource(Res.string.approve_untrusted_plugin))
                }
            }
        }
    }
}
