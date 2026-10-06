package com.kitakkun.jetwhale.host.settings.general

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.model.AppearanceSettings
import com.kitakkun.jetwhale.host.model.DebuggingToolsDiagnostics
import com.kitakkun.jetwhale.host.model.HostUpdateState
import com.kitakkun.jetwhale.host.settings.SettingsPresenterContext
import soil.query.compose.rememberMutation

@Composable
context(presenterContext: SettingsPresenterContext)
fun generalSettingsScreenPresenter(
    screenChannel: ScreenChannel<GeneralSettingsScreenAction, Nothing>,
    automaticallyWireADBTransport: Boolean,
    followAiOperationEnabled: Boolean,
    checkForUpdatesOnStartup: Boolean,
    appearanceSettings: AppearanceSettings,
    diagnostics: DebuggingToolsDiagnostics,
    hostUpdateState: HostUpdateState,
): GeneralSettingsScreenUiState {
    val appLanguageMutation = rememberMutation(presenterContext.appLanguageMutationKey)
    val appColorSchemeMutation = rememberMutation(presenterContext.appColorSchemeMutationKey)
    val adbAutoPortMappingMutation = rememberMutation(presenterContext.adbAutoPortMappingMutationKey)
    val followAiOperationMutation = rememberMutation(presenterContext.followAiOperationMutationKey)
    val checkForUpdatesOnStartupMutation = rememberMutation(presenterContext.checkForUpdatesOnStartupMutationKey)
    val checkForHostUpdateMutation = rememberMutation(presenterContext.checkForHostUpdateMutationKey)
    val downloadHostUpdateMutation = rememberMutation(presenterContext.downloadHostUpdateMutationKey)
    val cancelHostUpdateDownloadMutation = rememberMutation(presenterContext.cancelHostUpdateDownloadMutationKey)
    val restartToUpdateMutation = rememberMutation(presenterContext.restartToUpdateMutationKey)
    val tryHostVersionAgainMutation = rememberMutation(presenterContext.tryHostVersionAgainMutationKey)

    ActionEffect(screenChannel) { action ->
        when (action) {
            is GeneralSettingsScreenAction.ChangeAutomaticallyWireADBTransport -> {
                adbAutoPortMappingMutation.mutateAsync(action.shouldAutomaticallyWire)
            }

            is GeneralSettingsScreenAction.AppLanguageSelected -> {
                appLanguageMutation.mutateAsync(action.language)
            }

            is GeneralSettingsScreenAction.ColorSchemeSelected -> {
                appColorSchemeMutation.mutateAsync(action.colorSchemeId)
            }

            is GeneralSettingsScreenAction.ChangeFollowAiOperation -> {
                followAiOperationMutation.mutateAsync(action.enabled)
            }

            is GeneralSettingsScreenAction.ChangeCheckForUpdatesOnStartup -> {
                checkForUpdatesOnStartupMutation.mutateAsync(action.enabled)
            }

            is GeneralSettingsScreenAction.CheckForUpdates -> checkForHostUpdateMutation.mutateAsync(Unit)

            is GeneralSettingsScreenAction.DownloadUpdate -> downloadHostUpdateMutation.mutateAsync(Unit)

            is GeneralSettingsScreenAction.CancelUpdateDownload -> cancelHostUpdateDownloadMutation.mutateAsync(Unit)

            is GeneralSettingsScreenAction.RestartToUpdate -> restartToUpdateMutation.mutateAsync(Unit)

            is GeneralSettingsScreenAction.TryHostVersionAgain -> tryHostVersionAgainMutation.mutateAsync(action.version)
        }
    }

    return GeneralSettingsScreenUiState(
        automaticallyWireADBTransport = automaticallyWireADBTransport,
        selectedColorSchemeId = appearanceSettings.activeColorScheme,
        availableColorSchemes = appearanceSettings.availableColorSchemes,
        language = appearanceSettings.appLanguage,
        appDataPath = diagnostics.appDataPath,
        adbPath = diagnostics.adbPath,
        currentVersion = presenterContext.hostVersionInfo.version,
        followAiOperation = followAiOperationEnabled,
        updates = HostUpdatesUiState(
            status = hostUpdateState.status,
            setAsideVersion = hostUpdateState.setAside?.version,
            checkForUpdatesOnStartup = checkForUpdatesOnStartup,
        ),
    )
}
