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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.kitakkun.jetwhale.host.settings.no_plugins_installed
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(Res.string.failed_jar_path_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    uiState.failedJars.forEach { failedJar ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = failedJar.jarPath,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = failedJar.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
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

    LazyColumn(
        contentPadding = SettingsScreenScaffoldPageContentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        // Installed plugins: header with the install actions kept alongside it, then one card per
        // plugin. The whole page is a single scrollable list so no section can squeeze another out
        // of view when the window is short.
        item(key = "installed_header") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.installed_plugins),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onClickAddPlugin,
                    enabled = !uiState.isInstalling,
                ) {
                    Text(stringResource(Res.string.add_plugin_from_file), maxLines = 1)
                }
                TextButton(
                    onClick = onClickInstallFromMaven,
                    enabled = !uiState.isInstalling,
                ) {
                    Text(stringResource(Res.string.install_from_maven), maxLines = 1)
                }
            }
        }
        if (uiState.isInstalling) {
            item(key = "install_progress") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        uiState.installError?.let { error ->
            item(key = "install_error") {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(12.dp),
                )
            }
        }
        items(
            items = uiState.plugins,
            // Prefixed so an official catalog entry for the same plugin id cannot collide with it
            // in this single LazyColumn.
            key = { plugin -> "installed:${plugin.id}" },
        ) { plugin ->
            InstalledPluginRow(plugin = plugin)
        }
        if (uiState.plugins.isEmpty()) {
            item(key = "no_plugins") {
                Text(
                    text = stringResource(Res.string.no_plugins_installed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
            }
        }
        if (uiState.failedJars.isNotEmpty()) {
            item(key = "failed_jars") {
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
                            " (${uiState.failedJars.size})",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (uiState.untrustedJarPaths.isNotEmpty()) {
            item(key = "untrusted_jars") {
                UntrustedPluginsSection(
                    untrustedJarPaths = uiState.untrustedJarPaths,
                    onApprove = onApproveUntrustedJar,
                )
            }
        }
        if (uiState.officialPlugins.isNotEmpty()) {
            item(key = "official_header") {
                Text(
                    text = stringResource(Res.string.official_plugins),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(
                items = uiState.officialPlugins,
                key = { official -> "official:${official.plugin.pluginId}" },
            ) { officialPlugin ->
                OfficialPluginRow(
                    uiState = officialPlugin,
                    installEnabled = !uiState.isInstalling,
                    onClickInstall = { onClickInstallOfficialPlugin(officialPlugin.plugin) },
                )
            }
        }
    }
}

@Composable
private fun InstalledPluginRow(
    plugin: com.kitakkun.jetwhale.host.settings.component.PluginInfoUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = plugin.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "v${plugin.version}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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

@Composable
private fun OfficialPluginRow(
    uiState: OfficialPluginUiState,
    installEnabled: Boolean,
    onClickInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = uiState.plugin.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = uiState.plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (uiState.isInstalled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(Res.string.official_plugin_installed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onClickInstall,
                    enabled = installEnabled,
                ) {
                    Text(
                        text = stringResource(Res.string.maven_install_install),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
