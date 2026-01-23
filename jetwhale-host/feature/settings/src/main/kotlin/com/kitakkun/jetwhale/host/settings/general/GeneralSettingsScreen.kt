package com.kitakkun.jetwhale.host.settings.general

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.host.settings.adb_executable_path
import com.kitakkun.jetwhale.host.settings.adb_support
import com.kitakkun.jetwhale.host.settings.adb_unavailable
import com.kitakkun.jetwhale.host.settings.appearance
import com.kitakkun.jetwhale.host.settings.application_data_directory
import com.kitakkun.jetwhale.host.settings.automatically_wire_adb_transport
import com.kitakkun.jetwhale.host.settings.check_for_updates
import com.kitakkun.jetwhale.host.settings.check_for_updates_on_startup
import com.kitakkun.jetwhale.host.settings.component.DropdownSettingsItemView
import com.kitakkun.jetwhale.host.settings.component.SettingOptionView
import com.kitakkun.jetwhale.host.settings.component.SettingsItemRow
import com.kitakkun.jetwhale.host.settings.component.SwitchSettingsItemView
import com.kitakkun.jetwhale.host.settings.health_check
import com.kitakkun.jetwhale.host.settings.language_option
import com.kitakkun.jetwhale.host.settings.maintenance
import com.kitakkun.jetwhale.host.settings.theme_option
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
                SwitchSettingsItemView(
                    label = stringResource(Res.string.check_for_updates_on_startup),
                    isChecked = false,
                    onCheckedChange = { /* TODO: implement */ },
                )
                Button(onClick = {}) {
                    Text(stringResource(Res.string.check_for_updates))
                }
                Button(onClick = onClickOpenLogViewer) {
                    Text("View Application Logs")
                }
            }
        }
        item {
            SettingOptionView(stringResource(Res.string.health_check)) {
                SettingsItemRow(stringResource(Res.string.adb_executable_path)) {
                    Text(
                        text = uiState.adbPath.ifEmpty { stringResource(Res.string.adb_unavailable) }
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
        ),
        onCheckedChangePersistData = {},
        onAutomaticallyWireADBTransportChange = {},
        onSelectLanguage = {},
        onSelectColorScheme = {},
        onClickOpenAppDataPath = {},
        onClickOpenLogViewer = {},
    )
}
