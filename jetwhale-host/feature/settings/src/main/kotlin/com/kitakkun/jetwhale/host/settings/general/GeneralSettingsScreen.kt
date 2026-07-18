package com.kitakkun.jetwhale.host.settings.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId
import com.kitakkun.jetwhale.host.model.UpdateCheckResult
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.host.settings.adb_executable_path
import com.kitakkun.jetwhale.host.settings.adb_support
import com.kitakkun.jetwhale.host.settings.adb_unavailable
import com.kitakkun.jetwhale.host.settings.appearance
import com.kitakkun.jetwhale.host.settings.application_data_directory
import com.kitakkun.jetwhale.host.settings.automatically_wire_adb_transport
import com.kitakkun.jetwhale.host.settings.check_for_updates
import com.kitakkun.jetwhale.host.settings.checking_for_updates
import com.kitakkun.jetwhale.host.settings.component.DropdownSettingsItemView
import com.kitakkun.jetwhale.host.settings.component.SettingOptionView
import com.kitakkun.jetwhale.host.settings.component.SettingsItemRow
import com.kitakkun.jetwhale.host.settings.component.SwitchSettingsItemView
import com.kitakkun.jetwhale.host.settings.current_version
import com.kitakkun.jetwhale.host.settings.health_check
import com.kitakkun.jetwhale.host.settings.install_update
import com.kitakkun.jetwhale.host.settings.language_option
import com.kitakkun.jetwhale.host.settings.maintenance
import com.kitakkun.jetwhale.host.settings.open_download_page
import com.kitakkun.jetwhale.host.settings.theme_option
import com.kitakkun.jetwhale.host.settings.update_available
import com.kitakkun.jetwhale.host.settings.update_available_hint
import com.kitakkun.jetwhale.host.settings.update_check_failed
import com.kitakkun.jetwhale.host.settings.update_up_to_date
import com.kitakkun.jetwhale.host.settings.updates
import com.kitakkun.jetwhale.host.settings.view_application_logs
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
fun GeneralSettingsScreen(
    uiState: GeneralSettingsScreenUiState,
    onCheckedChangePersistData: (Boolean) -> Unit,
    onAutomaticallyWireADBTransportChange: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectColorScheme: (JetWhaleColorSchemeId) -> Unit,
    onClickOpenAppDataPath: () -> Unit,
    onClickOpenLogViewer: () -> Unit,
    onClickCheckForUpdates: () -> Unit,
    onClickInstallUpdate: () -> Unit,
    onClickOpenDownloadPage: (url: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = SettingsScreenScaffoldPageContentPadding,
    ) {
        item {
            SettingOptionView(
                label = stringResource(Res.string.appearance),
            ) {
                DropdownSettingsItemView(
                    label = stringResource(Res.string.language_option),
                    currentItem = uiState.language,
                    items = AppLanguage.entries,
                    onSelect = { onSelectLanguage(it) },
                    itemNameProvider = { it.displayName },
                )
                DropdownSettingsItemView(
                    label = stringResource(Res.string.theme_option),
                    currentItem = uiState.selectedColorSchemeId,
                    items = uiState.availableColorSchemes,
                    onSelect = { onSelectColorScheme(it) },
                    itemNameProvider = { it.id },
                )
            }
        }
        item {
            SettingOptionView(stringResource(Res.string.adb_support)) {
                SwitchSettingsItemView(
                    label = stringResource(Res.string.automatically_wire_adb_transport),
                    isChecked = uiState.automaticallyWireADBTransport,
                    onCheckedChange = onAutomaticallyWireADBTransportChange,
                )
            }
        }
        item {
            SettingOptionView(stringResource(Res.string.maintenance)) {
                SettingsItemRow(stringResource(Res.string.application_data_directory)) {
                    Card {
                        Text(uiState.appDataPath)
                    }
                    IconButton(onClick = onClickOpenAppDataPath) {
                        Icon(Icons.Default.FolderOpen, null)
                    }
                }
                Button(onClick = onClickOpenLogViewer) {
                    Text(stringResource(Res.string.view_application_logs))
                }
            }
        }
        item {
            SettingOptionView(stringResource(Res.string.updates)) {
                SettingsItemRow(stringResource(Res.string.current_version)) {
                    Card {
                        Text(uiState.currentVersion)
                    }
                }
                UpdateCheckStatusView(
                    isChecking = uiState.isCheckingForUpdates,
                    result = uiState.updateCheckResult,
                    error = uiState.updateCheckError,
                    onClickInstallUpdate = onClickInstallUpdate,
                    onClickOpenDownloadPage = onClickOpenDownloadPage,
                )
                Button(
                    onClick = onClickCheckForUpdates,
                    enabled = !uiState.isCheckingForUpdates,
                ) {
                    Text(stringResource(Res.string.check_for_updates))
                }
            }
        }
        item {
            SettingOptionView(stringResource(Res.string.health_check)) {
                SettingsItemRow(stringResource(Res.string.adb_executable_path)) {
                    Text(
                        text = uiState.adbPath.ifEmpty { stringResource(Res.string.adb_unavailable) },
                    )
                    Spacer(Modifier.width(8.dp))
                    if (uiState.adbPath.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCheckStatusView(
    isChecking: Boolean,
    result: UpdateCheckResult?,
    error: String?,
    onClickInstallUpdate: () -> Unit,
    onClickOpenDownloadPage: (url: String) -> Unit,
) {
    when {
        isChecking -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(Res.string.checking_for_updates))
            }
        }

        error != null -> {
            Text(
                text = stringResource(Res.string.update_check_failed, error),
                color = MaterialTheme.colorScheme.error,
            )
        }

        result == null -> Unit

        result.updateAvailable -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.update_available, result.latestVersion),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(Res.string.update_available_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (result.canInstallInApp) {
                            Button(onClick = onClickInstallUpdate) {
                                Text(stringResource(Res.string.install_update))
                            }
                        }
                        OutlinedButton(onClick = { onClickOpenDownloadPage(result.downloadPageUrl) }) {
                            Text(stringResource(Res.string.open_download_page))
                        }
                    }
                }
            }
        }

        else -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                )
                Text(stringResource(Res.string.update_up_to_date))
            }
        }
    }
}

@Preview
@Composable
private fun GeneralSettingsScreenPreview() {
    GeneralSettingsScreen(
        uiState = GeneralSettingsScreenUiState(
            automaticallyWireADBTransport = true,
            selectedColorSchemeId = JetWhaleColorSchemeId.BuiltInDynamic,
            availableColorSchemes = persistentListOf(),
            language = AppLanguage.English,
            appDataPath = "~/.jetwhale",
            adbPath = "/path/to/adb",
            currentVersion = "1.0.0-alpha08",
            isCheckingForUpdates = false,
            updateCheckResult = null,
            updateCheckError = null,
        ),
        onCheckedChangePersistData = {},
        onAutomaticallyWireADBTransportChange = {},
        onSelectLanguage = {},
        onSelectColorScheme = {},
        onClickOpenAppDataPath = {},
        onClickOpenLogViewer = {},
        onClickCheckForUpdates = {},
        onClickInstallUpdate = {},
        onClickOpenDownloadPage = {},
    )
}
