package com.kitakkun.jetwhale.host.settings.general

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.architecture.ActionEffect
import com.kitakkun.jetwhale.host.architecture.ScreenChannel
import com.kitakkun.jetwhale.host.model.AppearanceSettings
import com.kitakkun.jetwhale.host.model.DebuggerBehaviorSettings
import com.kitakkun.jetwhale.host.model.DebuggingToolsDiagnostics
import com.kitakkun.jetwhale.host.settings.SettingsPresenterContext
import soil.query.compose.rememberMutation

@Composable
context(presenterContext: SettingsPresenterContext)
fun generalSettingsScreenPresenter(
    screenChannel: ScreenChannel<GeneralSettingsScreenAction, Nothing>,
    debuggerBehaviorSettings: DebuggerBehaviorSettings,
    appearanceSettings: AppearanceSettings,
    diagnostics: DebuggingToolsDiagnostics,
): GeneralSettingsScreenUiState {
    val appLanguageMutation = rememberMutation(presenterContext.appLanguageMutationKey)
    val appColorSchemeMutation = rememberMutation(presenterContext.appColorSchemeMutationKey)
    val adbAutoPortMappingMutation = rememberMutation(presenterContext.adbAutoPortMappingMutationKey)

    ActionEffect(screenChannel) { action ->
        when (action) {
            is GeneralSettingsScreenAction.ChangePersistData -> {
            }

            is GeneralSettingsScreenAction.ChangeAutomaticallyWireADBTransport -> {
                adbAutoPortMappingMutation.mutateAsync(action.shouldAutomaticallyWire)
            }

            is GeneralSettingsScreenAction.AppLanguageSelected -> {
                appLanguageMutation.mutateAsync(action.language)
            }

            is GeneralSettingsScreenAction.ColorSchemeSelected -> {
                appColorSchemeMutation.mutateAsync(action.colorSchemeId)
            }
        }
    }

    return GeneralSettingsScreenUiState(
        automaticallyWireADBTransport = debuggerBehaviorSettings.adbAutoPortMappingEnabled,
        selectedColorSchemeId = appearanceSettings.activeColorScheme,
        availableColorSchemes = appearanceSettings.availableColorSchemes,
        language = appearanceSettings.appLanguage,
        appDataPath = diagnostics.appDataPath,
        adbPath = diagnostics.adbPath,
    )
}
