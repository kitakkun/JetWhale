package com.kitakkun.jetwhale.debugger.host.settings

import com.kitakkun.jetwhale.debugger.host.architecture.ScreenContext
import com.kitakkun.jetwhale.debugger.host.model.AdbAutoPortMappingMutationKey
import com.kitakkun.jetwhale.debugger.host.model.AppColorSchemeMutationKey
import com.kitakkun.jetwhale.debugger.host.model.AppLanguageMutationKey
import com.kitakkun.jetwhale.debugger.host.model.AppearanceSettingsSubscriptionKey
import com.kitakkun.jetwhale.debugger.host.model.DiagnosticsQueryKey
import com.kitakkun.jetwhale.debugger.host.model.LoadedPluginsMetaDataSubscriptionKey
import com.kitakkun.jetwhale.debugger.host.model.PluginInstallMutationKey
import com.kitakkun.jetwhale.debugger.host.model.ServerStatusSubscriptionKey
import com.kitakkun.jetwhale.debugger.host.model.SettingsSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension

@ContributesTo(AppScope::class)
@GraphExtension
interface SettingsScreenContext : ScreenContext {
    val settingsSubscriptionKey: SettingsSubscriptionKey
    val appearanceSettingsSubscriptionKey: AppearanceSettingsSubscriptionKey
    val diagnosticsQueryKey: DiagnosticsQueryKey
    val appLanguageMutationKey: AppLanguageMutationKey
    val appColorSchemeMutationKey: AppColorSchemeMutationKey
    val loadedPluginsMetaDataSubscriptionKey: LoadedPluginsMetaDataSubscriptionKey
    val serverStatusSubscriptionKey: ServerStatusSubscriptionKey
    val pluginInstallMutationKey: PluginInstallMutationKey
    val adbAutoPortMappingMutationKey: AdbAutoPortMappingMutationKey

    @GraphExtension.Factory
    fun interface Factory {
        fun createSettingsScreenContext(): SettingsScreenContext
    }
}
