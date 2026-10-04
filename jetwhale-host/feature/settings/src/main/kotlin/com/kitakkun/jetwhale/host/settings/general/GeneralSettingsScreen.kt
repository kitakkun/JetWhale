package com.kitakkun.jetwhale.host.settings.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.host.settings.adb_executable_path
import com.kitakkun.jetwhale.host.settings.adb_support
import com.kitakkun.jetwhale.host.settings.adb_unavailable
import com.kitakkun.jetwhale.host.settings.appearance
import com.kitakkun.jetwhale.host.settings.application_data_directory
import com.kitakkun.jetwhale.host.settings.automatically_wire_adb_transport
import com.kitakkun.jetwhale.host.settings.component.DropdownSettingsItemView
import com.kitakkun.jetwhale.host.settings.component.SettingOptionView
import com.kitakkun.jetwhale.host.settings.component.SettingsItemRow
import com.kitakkun.jetwhale.host.settings.component.SwitchSettingsItemView
import com.kitakkun.jetwhale.host.settings.current_version
import com.kitakkun.jetwhale.host.settings.follow_ai_operation
import com.kitakkun.jetwhale.host.settings.follow_ai_operation_description
import com.kitakkun.jetwhale.host.settings.health_check
import com.kitakkun.jetwhale.host.settings.language_option
import com.kitakkun.jetwhale.host.settings.maintenance
import com.kitakkun.jetwhale.host.settings.settings_page_ai_activity
import com.kitakkun.jetwhale.host.settings.theme_option
import com.kitakkun.jetwhale.host.settings.view_application_logs
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
fun GeneralSettingsScreen(
    page: SettingsScreenPage,
    uiState: GeneralSettingsScreenUiState,
    onAutomaticallyWireADBTransportChange: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectColorScheme: (JetWhaleColorSchemeId) -> Unit,
    onClickOpenAppDataPath: () -> Unit,
    onClickOpenLogViewer: () -> Unit,
    onFollowAiOperationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = SettingsScreenScaffoldPageContentPadding,
    ) {
        if (page == SettingsScreenPage.Appearance) {
            item {
                AppearanceSection(
                    language = uiState.language,
                    selectedColorSchemeId = uiState.selectedColorSchemeId,
                    availableColorSchemes = uiState.availableColorSchemes,
                    onSelectLanguage = onSelectLanguage,
                    onSelectColorScheme = onSelectColorScheme,
                )
            }
        }
        if (page == SettingsScreenPage.Adb) {
            item {
                AdbSupportSection(
                    automaticallyWireADBTransport = uiState.automaticallyWireADBTransport,
                    onAutomaticallyWireADBTransportChange = onAutomaticallyWireADBTransportChange,
                )
            }
        }
        if (page == SettingsScreenPage.AiActivity) {
            item {
                AiActivitySection(
                    followAiOperation = uiState.followAiOperation,
                    onFollowAiOperationChange = onFollowAiOperationChange,
                )
            }
        }
        if (page == SettingsScreenPage.Application) {
            item {
                MaintenanceSection(
                    currentVersion = uiState.currentVersion,
                    appDataPath = uiState.appDataPath,
                    onClickOpenAppDataPath = onClickOpenAppDataPath,
                    onClickOpenLogViewer = onClickOpenLogViewer,
                )
            }
        }
        if (page == SettingsScreenPage.Adb) {
            item { AdbHealthCheckSection(adbPath = uiState.adbPath) }
        }
    }
}

@Composable
private fun AppearanceSection(
    language: AppLanguage,
    selectedColorSchemeId: JetWhaleColorSchemeId,
    availableColorSchemes: List<JetWhaleColorSchemeId>,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectColorScheme: (JetWhaleColorSchemeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingOptionView(
        label = stringResource(Res.string.appearance),
        modifier = modifier,
    ) {
        DropdownSettingsItemView(
            label = stringResource(Res.string.language_option),
            currentItem = language,
            items = AppLanguage.entries,
            onSelect = onSelectLanguage,
            itemNameProvider = AppLanguage::displayName,
        )
        DropdownSettingsItemView(
            label = stringResource(Res.string.theme_option),
            currentItem = selectedColorSchemeId,
            items = availableColorSchemes,
            onSelect = onSelectColorScheme,
            itemNameProvider = JetWhaleColorSchemeId::id,
        )
    }
}

@Composable
private fun AdbSupportSection(
    automaticallyWireADBTransport: Boolean,
    onAutomaticallyWireADBTransportChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingOptionView(
        label = stringResource(Res.string.adb_support),
        modifier = modifier,
    ) {
        SwitchSettingsItemView(
            label = stringResource(Res.string.automatically_wire_adb_transport),
            isChecked = automaticallyWireADBTransport,
            onCheckedChange = onAutomaticallyWireADBTransportChange,
        )
    }
}

@Composable
private fun AiActivitySection(
    followAiOperation: Boolean,
    onFollowAiOperationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingOptionView(
        label = stringResource(Res.string.settings_page_ai_activity),
        modifier = modifier,
    ) {
        SwitchSettingsItemView(
            label = stringResource(Res.string.follow_ai_operation),
            isChecked = followAiOperation,
            onCheckedChange = onFollowAiOperationChange,
        )
        JwText(
            text = stringResource(Res.string.follow_ai_operation_description),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun MaintenanceSection(
    currentVersion: String,
    appDataPath: String,
    onClickOpenAppDataPath: () -> Unit,
    onClickOpenLogViewer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingOptionView(
        label = stringResource(Res.string.maintenance),
        modifier = modifier,
    ) {
        SettingsItemRow(stringResource(Res.string.current_version)) {
            JwText(
                text = currentVersion,
                style = JwTheme.textStyles.code,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JwText(
                text = stringResource(Res.string.application_data_directory),
                modifier = Modifier.widthIn(min = 120.dp),
            )
            SelectionContainer(modifier = Modifier.weight(1f)) {
                JwText(
                    text = appDataPath,
                    style = JwTheme.textStyles.code,
                )
            }
            JwIconButton(
                onClick = onClickOpenAppDataPath,
                tooltip = stringResource(Res.string.application_data_directory),
            ) {
                JwIcon(Icons.Default.FolderOpen, null)
            }
        }
        JwButton(
            text = stringResource(Res.string.view_application_logs),
            onClick = onClickOpenLogViewer,
            style = JwButtonStyle.Primary,
        )
    }
}

@Composable
private fun AdbHealthCheckSection(
    adbPath: String,
    modifier: Modifier = Modifier,
) {
    SettingOptionView(
        label = stringResource(Res.string.health_check),
        modifier = modifier,
    ) {
        SettingsItemRow(stringResource(Res.string.adb_executable_path)) {
            JwText(
                text = adbPath.ifEmpty { stringResource(Res.string.adb_unavailable) },
            )
            Spacer(Modifier.width(8.dp))
            JwIcon(
                imageVector = if (adbPath.isNotEmpty()) Icons.Default.Check else Icons.Default.Warning,
                tint = if (adbPath.isNotEmpty()) JwTone.Success.color else JwTone.Warning.color,
                contentDescription = null,
            )
        }
    }
}

@Preview
@Composable
private fun GeneralSettingsScreenPreview() {
    JwTheme(darkTheme = false) {
        GeneralSettingsScreen(
            page = SettingsScreenPage.Appearance,
            uiState = GeneralSettingsScreenUiState(
                automaticallyWireADBTransport = true,
                selectedColorSchemeId = JetWhaleColorSchemeId.BuiltInDynamic,
                availableColorSchemes = persistentListOf(),
                language = AppLanguage.English,
                appDataPath = "~/.jetwhale",
                adbPath = "/path/to/adb",
                currentVersion = "1.0.0-alpha08",
                followAiOperation = true,
            ),
            onAutomaticallyWireADBTransportChange = {},
            onSelectLanguage = {},
            onSelectColorScheme = {},
            onClickOpenAppDataPath = {},
            onClickOpenLogViewer = {},
            onFollowAiOperationChange = {},
        )
    }
}
