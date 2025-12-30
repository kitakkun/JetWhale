package com.kitakkun.jetwhale.debugger.host.settings.general

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.debugger.host.architecture.EventEffect
import com.kitakkun.jetwhale.debugger.host.architecture.EventFlow
import com.kitakkun.jetwhale.debugger.host.model.AppearanceSettings
import com.kitakkun.jetwhale.debugger.host.model.DebuggerBehaviorSettings
import com.kitakkun.jetwhale.debugger.host.settings.SettingsScreenContext
import soil.query.compose.rememberMutation

@Composable
context(screenContext: SettingsScreenContext)
fun generalSettingsScreenPresenter(
    eventFlow: EventFlow<GeneralSettingsScreenEvent>,
    debuggerBehaviorSettings: DebuggerBehaviorSettings,
    appearanceSettings: AppearanceSettings,
): GeneralSettingsScreenUiState {
    val appLanguageMutation = rememberMutation(screenContext.appLanguageMutationKey)
    val appColorSchemeMutation = rememberMutation(screenContext.appColorSchemeMutationKey)
    val adbAutoPortMappingMutation = rememberMutation(screenContext.adbAutoPortMappingMutationKey)

    EventEffect(eventFlow) { event ->
        when (event) {
            is GeneralSettingsScreenEvent.ChangePersistData -> {
            }

            is GeneralSettingsScreenEvent.ChangeAutomaticallyWireADBTransport -> {
                adbAutoPortMappingMutation.mutate(event.shouldAutomaticallyWire)
            }

            is GeneralSettingsScreenEvent.AppLanguageSelected -> {
                appLanguageMutation.mutate(event.language)
            }

            is GeneralSettingsScreenEvent.ColorSchemeSelected -> {
                appColorSchemeMutation.mutate(event.colorSchemeId)
            }
        }
    }

    return GeneralSettingsScreenUiState(
        automaticallyWireADBTransport = debuggerBehaviorSettings.adbAutoPortMappingEnabled,
        selectedColorSchemeId = appearanceSettings.activeColorScheme,
        availableColorSchemes = appearanceSettings.availableColorSchemes,
        language = appearanceSettings.appLanguage,
        appDataPath = "~/.jetwhale", // TODO: fix hardcoded path
    )
}
