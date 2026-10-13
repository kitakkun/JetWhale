package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.InstalledPluginVersion
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.component.PluginInfoUiState
import com.kitakkun.jetwhale.host.settings.failed_to_load_plugins
import com.kitakkun.jetwhale.host.settings.installed_plugins
import com.kitakkun.jetwhale.host.settings.no_plugins_installed
import com.kitakkun.jetwhale.host.settings.remove_plugin_version
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.installedPluginsPageItems(
    plugins: ImmutableList<PluginInfoUiState>,
    failedJarCount: Int,
    onRemoveVersion: (PluginInfoUiState, InstalledPluginVersion) -> Unit,
    onClickFailedJars: () -> Unit,
) {
    item(key = "installed_header") {
        JwText(
            text = stringResource(Res.string.installed_plugins),
            style = JwTheme.textStyles.title,
        )
    }
    items(
        items = plugins,
        key = { plugin -> "installed:${plugin.id}" },
    ) { plugin ->
        InstalledPluginRow(plugin = plugin, onRemoveVersion = { version -> onRemoveVersion(plugin, version) })
    }
    if (plugins.isEmpty()) {
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
    if (failedJarCount > 0) {
        item(key = "failed_jars") {
            FailedJarsButton(
                failedJarCount = failedJarCount,
                onClick = onClickFailedJars,
            )
        }
    }
}

/** One plugin with each of its installed versions. */
@Composable
private fun InstalledPluginRow(
    plugin: PluginInfoUiState,
    onRemoveVersion: (InstalledPluginVersion) -> Unit,
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
            Column(horizontalAlignment = Alignment.End) {
                plugin.installedVersions.forEach { installedVersion ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        JwText(
                            text = "v${installedVersion.version}",
                            style = JwTheme.textStyles.label,
                            color = JwTheme.colors.textSecondary,
                            maxLines = 1,
                        )
                        if (installedVersion.removable) {
                            JwButton(
                                text = stringResource(Res.string.remove_plugin_version),
                                onClick = { onRemoveVersion(installedVersion) },
                                style = JwButtonStyle.Text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedJarsButton(
    failedJarCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwButton(
        text = stringResource(Res.string.failed_to_load_plugins) + " ($failedJarCount)",
        onClick = onClick,
        style = JwButtonStyle.Text,
        modifier = modifier,
        leadingIcon = {
            JwIcon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = JwTheme.colors.error,
            )
        },
    )
}
