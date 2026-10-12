package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.OfficialPlugin
import com.kitakkun.jetwhale.host.model.PluginInstallRequest
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.host.settings.close
import com.kitakkun.jetwhale.host.settings.component.PluginInfoUiState
import com.kitakkun.jetwhale.host.settings.dialog_ok
import com.kitakkun.jetwhale.host.settings.failed_jar_path_hint
import com.kitakkun.jetwhale.host.settings.failed_to_load_plugins
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
fun PluginSettingsScreen(
    page: SettingsScreenPage,
    uiState: PluginSettingsScreenUiState,
    onClickAddPlugin: () -> Unit,
    onApproveUntrustedJar: (String) -> Unit,
    onClickInstallFromMaven: () -> Unit,
    onClickInstallOfficialPlugin: (OfficialPlugin) -> Unit,
    onCancelInstall: (jobId: String) -> Unit,
    onRetryInstall: (PluginInstallRequest) -> Unit,
    onDismissInstall: (jobId: String) -> Unit,
    onChangeSignPluginTrustRegistry: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFailedJarsDialog by remember { mutableStateOf(false) }

    if (showFailedJarsDialog) {
        FailedJarsDialog(
            failedJars = uiState.failedJars,
            onDismiss = { showFailedJarsDialog = false },
        )
    }

    LazyColumn(
        contentPadding = SettingsScreenScaffoldPageContentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        when (page) {
            SettingsScreenPage.InstalledPlugins -> installedPluginsPageItems(
                plugins = uiState.plugins,
                failedJarCount = uiState.failedJars.size,
                onClickFailedJars = { showFailedJarsDialog = true },
            )

            SettingsScreenPage.AddPlugins -> addPluginsPageItems(
                uiState = uiState,
                onClickAddPlugin = onClickAddPlugin,
                onClickInstallFromMaven = onClickInstallFromMaven,
                onClickInstallOfficialPlugin = onClickInstallOfficialPlugin,
                onCancelInstall = onCancelInstall,
                onRetryInstall = onRetryInstall,
                onDismissInstall = onDismissInstall,
            )

            SettingsScreenPage.PluginSecurity -> pluginSecurityPageItems(
                untrustedJarPaths = uiState.untrustedJarPaths,
                signPluginTrustRegistry = uiState.signPluginTrustRegistry,
                onApproveUntrustedJar = onApproveUntrustedJar,
                onChangeSignPluginTrustRegistry = onChangeSignPluginTrustRegistry,
            )

            else -> Unit
        }
    }
}

@Composable
private fun FailedJarsDialog(
    failedJars: List<FailedPluginJar>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwDialog(
        onDismissRequest = onDismiss,
        closeLabel = stringResource(Res.string.close),
        title = stringResource(Res.string.failed_to_load_plugins),
        modifier = modifier,
        confirmButton = {
            JwButton(
                text = stringResource(Res.string.dialog_ok),
                onClick = onDismiss,
                style = JwButtonStyle.Text,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            JwText(
                text = stringResource(Res.string.failed_jar_path_hint),
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
            JwHorizontalDivider()
            failedJars.forEach { failedJar ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    JwText(text = failedJar.jarPath, style = JwTheme.textStyles.bodySmall)
                    JwText(
                        text = failedJar.reason,
                        style = JwTheme.textStyles.bodySmall,
                        color = JwTheme.colors.error,
                    )
                }
            }
        }
    }
}

/** Inset of an installed or official plugin row inside its panel. */
internal val PluginRowPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

@Preview
@Composable
private fun PluginSettingsScreenPreview() {
    JwTheme(darkTheme = false) {
        PluginSettingsScreen(
            page = SettingsScreenPage.InstalledPlugins,
            uiState = PluginSettingsScreenUiState(
                plugins = persistentListOf(
                    PluginInfoUiState(
                        name = "Network Inspector",
                        id = "com.kitakkun.jetwhale.network",
                        version = "1.0.0",
                    ),
                ),
                officialPlugins = persistentListOf(),
                failedJars = persistentListOf(),
                untrustedJarPaths = persistentListOf(),
                signPluginTrustRegistry = false,
                installJobs = persistentListOf(),
                isAddingFromFile = false,
                addFromFileError = null,
            ),
            onClickAddPlugin = {},
            onApproveUntrustedJar = {},
            onClickInstallFromMaven = {},
            onClickInstallOfficialPlugin = {},
            onCancelInstall = {},
            onRetryInstall = {},
            onDismissInstall = {},
            onChangeSignPluginTrustRegistry = {},
        )
    }
}
