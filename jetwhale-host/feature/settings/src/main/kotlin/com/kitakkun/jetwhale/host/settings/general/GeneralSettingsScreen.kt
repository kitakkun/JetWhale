package com.kitakkun.jetwhale.host.settings.general

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.AppLanguage
import com.kitakkun.jetwhale.host.model.HostUpdateFailure
import com.kitakkun.jetwhale.host.model.HostUpdateStatus
import com.kitakkun.jetwhale.host.model.JetWhaleColorSchemeId
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.SettingsScreenScaffoldPageContentPadding
import com.kitakkun.jetwhale.host.settings.adb_executable_path
import com.kitakkun.jetwhale.host.settings.adb_support
import com.kitakkun.jetwhale.host.settings.adb_unavailable
import com.kitakkun.jetwhale.host.settings.appearance
import com.kitakkun.jetwhale.host.settings.application_data_directory
import com.kitakkun.jetwhale.host.settings.automatically_wire_adb_transport
import com.kitakkun.jetwhale.host.settings.cancel_download
import com.kitakkun.jetwhale.host.settings.check_for_updates
import com.kitakkun.jetwhale.host.settings.check_for_updates_on_startup
import com.kitakkun.jetwhale.host.settings.checking_for_updates
import com.kitakkun.jetwhale.host.settings.component.DropdownSettingsItemView
import com.kitakkun.jetwhale.host.settings.component.SettingOptionView
import com.kitakkun.jetwhale.host.settings.component.SettingsItemRow
import com.kitakkun.jetwhale.host.settings.component.SwitchSettingsItemView
import com.kitakkun.jetwhale.host.settings.current_version
import com.kitakkun.jetwhale.host.settings.download_update
import com.kitakkun.jetwhale.host.settings.downloading_update
import com.kitakkun.jetwhale.host.settings.follow_ai_operation
import com.kitakkun.jetwhale.host.settings.follow_ai_operation_description
import com.kitakkun.jetwhale.host.settings.health_check
import com.kitakkun.jetwhale.host.settings.language_option
import com.kitakkun.jetwhale.host.settings.maintenance
import com.kitakkun.jetwhale.host.settings.open_release_page
import com.kitakkun.jetwhale.host.settings.restart_to_update
import com.kitakkun.jetwhale.host.settings.set_aside_version
import com.kitakkun.jetwhale.host.settings.settings_page_ai_activity
import com.kitakkun.jetwhale.host.settings.theme_option
import com.kitakkun.jetwhale.host.settings.try_again
import com.kitakkun.jetwhale.host.settings.update_available
import com.kitakkun.jetwhale.host.settings.update_available_hint
import com.kitakkun.jetwhale.host.settings.update_check_failed
import com.kitakkun.jetwhale.host.settings.update_failed
import com.kitakkun.jetwhale.host.settings.update_failure_bad_metadata
import com.kitakkun.jetwhale.host.settings.update_failure_corrupted
import com.kitakkun.jetwhale.host.settings.update_failure_could_not_save
import com.kitakkun.jetwhale.host.settings.update_failure_rate_limited
import com.kitakkun.jetwhale.host.settings.update_failure_unexpected_response
import com.kitakkun.jetwhale.host.settings.update_failure_unreachable
import com.kitakkun.jetwhale.host.settings.update_needs_new_installer
import com.kitakkun.jetwhale.host.settings.update_needs_new_installer_hint
import com.kitakkun.jetwhale.host.settings.update_no_build_for_this_computer
import com.kitakkun.jetwhale.host.settings.update_no_build_for_this_computer_hint
import com.kitakkun.jetwhale.host.settings.update_ready
import com.kitakkun.jetwhale.host.settings.update_ready_hint
import com.kitakkun.jetwhale.host.settings.update_up_to_date
import com.kitakkun.jetwhale.host.settings.updates
import com.kitakkun.jetwhale.host.settings.updates_not_managed
import com.kitakkun.jetwhale.host.settings.verifying_update
import com.kitakkun.jetwhale.host.settings.view_application_logs
import com.kitakkun.jetwhale.host.settings.view_host_log
import com.kitakkun.jetwhale.host.theme.LocalEmbeddedInIde
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwProgressIndicator
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.LocalJwContentColor
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
    onCheckForUpdatesOnStartupChange: (Boolean) -> Unit,
    onClickCheckForUpdates: () -> Unit,
    onClickDownloadUpdate: () -> Unit,
    onClickCancelUpdateDownload: () -> Unit,
    onClickRestartToUpdate: () -> Unit,
    onClickTryHostVersionAgain: (version: HostVersion) -> Unit,
    onClickViewHostLog: () -> Unit,
    onClickOpenReleasePage: (version: HostVersion?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEmbeddedInIde = LocalEmbeddedInIde.current
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
        if (page == SettingsScreenPage.Application && !isEmbeddedInIde) {
            item {
                UpdatesSection(
                    updates = uiState.updates,
                    onCheckForUpdatesOnStartupChange = onCheckForUpdatesOnStartupChange,
                    onClickCheckForUpdates = onClickCheckForUpdates,
                    onClickDownloadUpdate = onClickDownloadUpdate,
                    onClickCancelUpdateDownload = onClickCancelUpdateDownload,
                    onClickRestartToUpdate = onClickRestartToUpdate,
                    onClickTryHostVersionAgain = onClickTryHostVersionAgain,
                    onClickViewHostLog = onClickViewHostLog,
                    onClickOpenReleasePage = onClickOpenReleasePage,
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
private fun UpdatesSection(
    updates: HostUpdatesUiState,
    onCheckForUpdatesOnStartupChange: (Boolean) -> Unit,
    onClickCheckForUpdates: () -> Unit,
    onClickDownloadUpdate: () -> Unit,
    onClickCancelUpdateDownload: () -> Unit,
    onClickRestartToUpdate: () -> Unit,
    onClickTryHostVersionAgain: (version: HostVersion) -> Unit,
    onClickViewHostLog: () -> Unit,
    onClickOpenReleasePage: (version: HostVersion?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingOptionView(
        label = stringResource(Res.string.updates),
        modifier = modifier,
    ) {
        if (updates.status is HostUpdateStatus.NotManaged) {
            JwText(
                text = stringResource(Res.string.updates_not_managed),
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
            JwButton(
                text = stringResource(Res.string.open_release_page),
                onClick = { onClickOpenReleasePage(null) },
                style = JwButtonStyle.Secondary,
            )
            return@SettingOptionView
        }
        SwitchSettingsItemView(
            label = stringResource(Res.string.check_for_updates_on_startup),
            isChecked = updates.checkForUpdatesOnStartup,
            onCheckedChange = onCheckForUpdatesOnStartupChange,
        )
        HostUpdateStatusView(
            status = updates.status,
            onClickDownloadUpdate = onClickDownloadUpdate,
            onClickCancelUpdateDownload = onClickCancelUpdateDownload,
            onClickRestartToUpdate = onClickRestartToUpdate,
            onClickOpenReleasePage = onClickOpenReleasePage,
        )
        updates.setAsideVersion?.let { version ->
            UpdateNotice(
                title = stringResource(Res.string.set_aside_version, version.name),
                hint = null,
                tone = JwTone.Warning,
            ) {
                JwButton(
                    text = stringResource(Res.string.try_again),
                    onClick = { onClickTryHostVersionAgain(version) },
                    style = JwButtonStyle.Primary,
                )
                JwButton(
                    text = stringResource(Res.string.view_host_log),
                    onClick = onClickViewHostLog,
                    style = JwButtonStyle.Secondary,
                )
            }
        }
        if (updates.status !is HostUpdateStatus.Checking &&
            updates.status !is HostUpdateStatus.Downloading &&
            updates.status !is HostUpdateStatus.Verifying
        ) {
            JwButton(
                text = stringResource(Res.string.check_for_updates),
                onClick = onClickCheckForUpdates,
                style = JwButtonStyle.Primary,
            )
        }
    }
}

@Composable
private fun HostUpdateStatusView(
    status: HostUpdateStatus,
    onClickDownloadUpdate: () -> Unit,
    onClickCancelUpdateDownload: () -> Unit,
    onClickRestartToUpdate: () -> Unit,
    onClickOpenReleasePage: (version: HostVersion?) -> Unit,
) {
    when (status) {
        is HostUpdateStatus.NotManaged, is HostUpdateStatus.NotChecked -> Unit

        is HostUpdateStatus.Checking -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JwProgressIndicator()
            JwText(stringResource(Res.string.checking_for_updates))
        }

        is HostUpdateStatus.UpToDate -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JwIcon(imageVector = Icons.Default.Check, tint = JwTone.Success.color, contentDescription = null)
            JwText(stringResource(Res.string.update_up_to_date))
        }

        is HostUpdateStatus.Available -> UpdateNotice(
            title = stringResource(Res.string.update_available, status.version.name, megabytes(status.sizeBytes)),
            hint = stringResource(Res.string.update_available_hint),
            tone = JwTone.Info,
        ) {
            JwButton(text = stringResource(Res.string.download_update), onClick = onClickDownloadUpdate, style = JwButtonStyle.Primary)
            JwButton(
                text = stringResource(Res.string.open_release_page),
                onClick = { onClickOpenReleasePage(status.version) },
                style = JwButtonStyle.Secondary,
            )
        }

        is HostUpdateStatus.Downloading -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JwProgressIndicator()
            JwText(
                stringResource(
                    Res.string.downloading_update,
                    status.version.name,
                    megabytes(status.downloadedBytes),
                    megabytes(status.totalBytes),
                ),
            )
            JwButton(text = stringResource(Res.string.cancel_download), onClick = onClickCancelUpdateDownload, style = JwButtonStyle.Secondary)
        }

        is HostUpdateStatus.Verifying -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JwProgressIndicator()
            JwText(stringResource(Res.string.verifying_update, status.version.name))
            JwButton(text = stringResource(Res.string.cancel_download), onClick = onClickCancelUpdateDownload, style = JwButtonStyle.Secondary)
        }

        is HostUpdateStatus.ReadyToRestart -> UpdateNotice(
            title = stringResource(Res.string.update_ready, status.version.name),
            hint = stringResource(Res.string.update_ready_hint),
            tone = JwTone.Success,
        ) {
            JwButton(text = stringResource(Res.string.restart_to_update), onClick = onClickRestartToUpdate, style = JwButtonStyle.Primary)
        }

        is HostUpdateStatus.NeedsNewInstaller -> UpdateNotice(
            title = stringResource(Res.string.update_needs_new_installer, status.version.name),
            hint = stringResource(Res.string.update_needs_new_installer_hint),
            tone = JwTone.Warning,
        ) {
            JwButton(
                text = stringResource(Res.string.open_release_page),
                onClick = { onClickOpenReleasePage(status.version) },
                style = JwButtonStyle.Primary,
            )
        }

        is HostUpdateStatus.NoBuildForThisComputer -> UpdateNotice(
            title = stringResource(Res.string.update_no_build_for_this_computer, status.version.name),
            hint = stringResource(Res.string.update_no_build_for_this_computer_hint),
            tone = JwTone.Warning,
        ) {
            JwButton(
                text = stringResource(Res.string.open_release_page),
                onClick = { onClickOpenReleasePage(status.version) },
                style = JwButtonStyle.Secondary,
            )
        }

        is HostUpdateStatus.CheckFailed -> JwText(
            text = stringResource(Res.string.update_check_failed, failureMessage(status.failure)),
            color = JwTheme.colors.error,
        )

        is HostUpdateStatus.DownloadFailed -> JwText(
            text = stringResource(Res.string.update_failed, failureMessage(status.failure)),
            color = JwTheme.colors.error,
        )
    }
}

@Composable
private fun failureMessage(failure: HostUpdateFailure): String = when (failure) {
    is HostUpdateFailure.RateLimited -> stringResource(Res.string.update_failure_rate_limited)
    is HostUpdateFailure.Unreachable -> stringResource(Res.string.update_failure_unreachable)
    is HostUpdateFailure.UnexpectedResponse -> stringResource(Res.string.update_failure_unexpected_response, failure.statusCode.toString())
    is HostUpdateFailure.BadMetadata -> stringResource(Res.string.update_failure_bad_metadata, failure.reason)
    is HostUpdateFailure.Corrupted -> stringResource(Res.string.update_failure_corrupted)
    is HostUpdateFailure.CouldNotSave -> stringResource(Res.string.update_failure_could_not_save)
}

@Composable
private fun UpdateNotice(
    title: String,
    hint: String?,
    tone: JwTone,
    buttons: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.containerColor, JwShapes.small)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(LocalJwContentColor provides tone.onContainerColor) {
            JwText(text = title, style = JwTheme.textStyles.subtitle)
            if (hint != null) JwText(text = hint, style = JwTheme.textStyles.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = buttons)
        }
    }
}

private fun megabytes(bytes: Long): String = "%.1f".format(bytes / BYTES_PER_MEGABYTE)

private const val BYTES_PER_MEGABYTE = 1_048_576.0

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
private fun GeneralSettingsScreenPreview(
    @PreviewParameter(GeneralSettingsScreenPreviewStates::class) state: GeneralSettingsScreenPreviewState,
) {
    JwTheme(darkTheme = false) {
        GeneralSettingsScreen(
            page = state.page,
            uiState = state.uiState,
            onAutomaticallyWireADBTransportChange = {},
            onSelectLanguage = {},
            onSelectColorScheme = {},
            onClickOpenAppDataPath = {},
            onClickOpenLogViewer = {},
            onFollowAiOperationChange = {},
            onCheckForUpdatesOnStartupChange = {},
            onClickCheckForUpdates = {},
            onClickDownloadUpdate = {},
            onClickCancelUpdateDownload = {},
            onClickRestartToUpdate = {},
            onClickTryHostVersionAgain = {},
            onClickViewHostLog = {},
            onClickOpenReleasePage = {},
        )
    }
}

private class GeneralSettingsScreenPreviewState(
    val page: SettingsScreenPage,
    val uiState: GeneralSettingsScreenUiState,
)

private class GeneralSettingsScreenPreviewStates : PreviewParameterProvider<GeneralSettingsScreenPreviewState> {
    private val uiState = GeneralSettingsScreenUiState(
        automaticallyWireADBTransport = true,
        selectedColorSchemeId = JetWhaleColorSchemeId.BuiltInDynamic,
        availableColorSchemes = persistentListOf(),
        language = AppLanguage.English,
        appDataPath = "~/.jetwhale",
        adbPath = "/path/to/adb",
        currentVersion = "1.0.0-alpha13",
        followAiOperation = true,
        updates = HostUpdatesUiState(status = HostUpdateStatus.UpToDate, setAsideVersion = null, checkForUpdatesOnStartup = true),
    )

    override val values: Sequence<GeneralSettingsScreenPreviewState> = sequenceOf(
        GeneralSettingsScreenPreviewState(SettingsScreenPage.Appearance, uiState),
        GeneralSettingsScreenPreviewState(
            SettingsScreenPage.Application,
            uiState.copy(
                updates = uiState.updates.copy(
                    status = HostUpdateStatus.Available(version = previewVersion("1.0.0-alpha14"), sizeBytes = 125_156_159),
                    setAsideVersion = previewVersion("1.0.0-alpha12"),
                ),
            ),
        ),
        GeneralSettingsScreenPreviewState(
            SettingsScreenPage.Application,
            uiState.copy(updates = uiState.updates.copy(status = HostUpdateStatus.ReadyToRestart(version = previewVersion("1.0.0-alpha14")))),
        ),
    )

    private fun previewVersion(name: String): HostVersion = checkNotNull(HostVersion.parse(name))
}
