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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.kitakkun.jetwhale.host.model.HostOs
import com.kitakkun.jetwhale.host.model.OfficialPlugin
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.host.settings.add_plugin_from_file
import com.kitakkun.jetwhale.host.settings.approve_untrusted_plugin
import com.kitakkun.jetwhale.host.settings.close
import com.kitakkun.jetwhale.host.settings.component.SettingOptionView
import com.kitakkun.jetwhale.host.settings.component.SwitchSettingsItemView
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
import com.kitakkun.jetwhale.host.settings.plugin_security
import com.kitakkun.jetwhale.host.settings.sign_plugin_trust_registry
import com.kitakkun.jetwhale.host.settings.sign_plugin_trust_registry_hint
import com.kitakkun.jetwhale.host.settings.sign_plugin_trust_registry_hint_linux
import com.kitakkun.jetwhale.host.settings.sign_plugin_trust_registry_hint_macos
import com.kitakkun.jetwhale.host.settings.sign_plugin_trust_registry_hint_windows
import com.kitakkun.jetwhale.host.settings.untrusted_jar_hint
import com.kitakkun.jetwhale.host.settings.untrusted_plugins
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun PluginSettingsScreen(
    page: SettingsScreenPage,
    uiState: PluginSettingsScreenUiState,
    onClickAddPlugin: () -> Unit,
    onApproveUntrustedJar: (String) -> Unit,
    onClickInstallFromMaven: () -> Unit,
    onClickInstallOfficialPlugin: (OfficialPlugin) -> Unit,
    onChangeSignPluginTrustRegistry: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFailedJarsDialog by remember { mutableStateOf(false) }

    if (showFailedJarsDialog) {
        JwDialog(
            onDismissRequest = { showFailedJarsDialog = false },
            closeLabel = stringResource(Res.string.close),
            title = stringResource(Res.string.failed_to_load_plugins),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    JwText(
                        text = stringResource(Res.string.failed_jar_path_hint),
                        style = JwTheme.textStyles.bodySmall,
                        color = JwTheme.colors.textSecondary,
                    )
                    HorizontalDivider()
                    uiState.failedJars.forEach { failedJar ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            JwText(
                                text = failedJar.jarPath,
                                style = JwTheme.textStyles.bodySmall,
                            )
                            JwText(
                                text = failedJar.reason,
                                style = JwTheme.textStyles.bodySmall,
                                color = JwTheme.colors.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                JwButton(
                    text = stringResource(Res.string.dialog_ok),
                    onClick = { showFailedJarsDialog = false },
                    style = JwButtonStyle.Text,
                )
            },
        )
    }

    LazyColumn(
        contentPadding = SettingsScreenScaffoldPageContentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        // One list serving three pages of the Plugins section: each block declares which page it
        // belongs to, so the section's Root keeps a single set of subscriptions.
        if (page == SettingsScreenPage.InstalledPlugins) {
            item(key = "installed_header") {
                JwText(
                    text = stringResource(Res.string.installed_plugins),
                    style = JwTheme.textStyles.title,
                )
            }
        }
        // The install actions sit with the official catalog rather than on the installed list: they
        // are two more ways in, and splitting them across pages hid that they are the same choice.
        if (page == SettingsScreenPage.AddPlugins) {
            item(key = "add_actions") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    JwButton(
                        text = stringResource(Res.string.add_plugin_from_file),
                        onClick = onClickAddPlugin,
                        enabled = !uiState.isInstalling,
                        style = JwButtonStyle.Text,
                    )
                    JwButton(
                        text = stringResource(Res.string.install_from_maven),
                        onClick = onClickInstallFromMaven,
                        enabled = !uiState.isInstalling,
                        style = JwButtonStyle.Text,
                    )
                }
            }
        }
        if (page == SettingsScreenPage.AddPlugins && uiState.isInstalling) {
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
                        JwText(
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
                            style = JwTheme.textStyles.bodySmall,
                            color = JwTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // Beside the progress it replaces: an install only starts from this page, so its failure has
        // no business appearing over the installed list or the security settings.
        if (page == SettingsScreenPage.AddPlugins) {
            uiState.installError?.let { error ->
                item(key = "install_error") {
                    JwText(
                        text = error,
                        color = JwTheme.colors.onErrorContainer,
                        style = JwTheme.textStyles.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = JwTheme.colors.errorContainer,
                                shape = JwShapes.small,
                            )
                            .padding(12.dp),
                    )
                }
            }
        }
        if (page == SettingsScreenPage.InstalledPlugins) {
            items(
                items = uiState.plugins,
                // Prefixed so an official catalog entry for the same plugin id cannot collide with it
                // in this single LazyColumn.
                key = { plugin -> "installed:${plugin.id}" },
            ) { plugin ->
                InstalledPluginRow(plugin = plugin)
            }
        }
        if (page == SettingsScreenPage.InstalledPlugins && uiState.plugins.isEmpty()) {
            item(key = "no_plugins") {
                JwText(
                    text = stringResource(Res.string.no_plugins_installed),
                    style = JwTheme.textStyles.body,
                    color = JwTheme.colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
            }
        }
        if (page == SettingsScreenPage.InstalledPlugins && uiState.failedJars.isNotEmpty()) {
            item(key = "failed_jars") {
                JwButton(
                    text = stringResource(Res.string.failed_to_load_plugins) + " (${uiState.failedJars.size})",
                    onClick = { showFailedJarsDialog = true },
                    style = JwButtonStyle.Text,
                    leadingIcon = {
                        JwIcon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = JwTheme.colors.error,
                        )
                    },
                )
            }
        }
        if (page == SettingsScreenPage.PluginSecurity && uiState.untrustedJarPaths.isNotEmpty()) {
            item(key = "untrusted_jars") {
                UntrustedPluginsSection(
                    untrustedJarPaths = uiState.untrustedJarPaths,
                    onApprove = onApproveUntrustedJar,
                )
            }
        }
        if (page == SettingsScreenPage.AddPlugins && uiState.officialPlugins.isNotEmpty()) {
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
                    installEnabled = !uiState.isInstalling,
                    onClickInstall = { onClickInstallOfficialPlugin(officialPlugin.plugin) },
                )
            }
        }
        if (page == SettingsScreenPage.PluginSecurity) {
            item(key = "trust_registry_signing") {
                SettingOptionView(label = stringResource(Res.string.plugin_security)) {
                    SwitchSettingsItemView(
                        label = stringResource(Res.string.sign_plugin_trust_registry),
                        isChecked = uiState.signPluginTrustRegistry,
                        onCheckedChange = onChangeSignPluginTrustRegistry,
                    )
                    // Append only the current OS's credential-store behavior — the prompt story differs
                    // per platform (macOS prompts, Windows DPAPI is silent, Linux depends on the keyring).
                    val osHint = when (HostOs.current) {
                        HostOs.MAC -> Res.string.sign_plugin_trust_registry_hint_macos
                        HostOs.WINDOWS -> Res.string.sign_plugin_trust_registry_hint_windows
                        else -> Res.string.sign_plugin_trust_registry_hint_linux
                    }
                    JwText(
                        text = "${stringResource(Res.string.sign_plugin_trust_registry_hint)} ${stringResource(osHint)}",
                        style = JwTheme.textStyles.bodySmall,
                        color = JwTheme.colors.textSecondary,
                    )
                }
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
        shape = JwShapes.medium,
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
                JwText(
                    text = plugin.name,
                    style = JwTheme.textStyles.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                JwText(
                    text = plugin.id,
                    style = JwTheme.textStyles.bodySmall,
                    color = JwTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            JwText(
                text = "v${plugin.version}",
                style = JwTheme.textStyles.label,
                color = JwTheme.colors.textSecondary,
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
                color = JwTheme.colors.errorContainer,
                shape = JwShapes.small,
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
                tint = JwTheme.colors.error,
            )
            JwText(
                text = stringResource(Res.string.untrusted_plugins) + " (${untrustedJarPaths.size})",
                style = JwTheme.textStyles.subtitle,
                color = JwTheme.colors.onErrorContainer,
            )
        }
        JwText(
            text = stringResource(Res.string.untrusted_jar_hint),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.onErrorContainer,
        )
        untrustedJarPaths.forEach { path ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JwText(
                    text = path,
                    style = JwTheme.textStyles.bodySmall,
                    color = JwTheme.colors.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                JwButton(
                    text = stringResource(Res.string.approve_untrusted_plugin),
                    onClick = { onApprove(path) },
                    style = JwButtonStyle.Primary,
                )
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
        shape = JwShapes.medium,
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
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = JwTheme.colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    JwText(
                        text = stringResource(Res.string.official_plugin_installed),
                        style = JwTheme.textStyles.label,
                        color = JwTheme.colors.accent,
                        maxLines = 1,
                    )
                }
            } else {
                JwButton(
                    text = stringResource(Res.string.maven_install_install),
                    onClick = onClickInstall,
                    enabled = installEnabled,
                    style = JwButtonStyle.Secondary,
                )
            }
        }
    }
}
