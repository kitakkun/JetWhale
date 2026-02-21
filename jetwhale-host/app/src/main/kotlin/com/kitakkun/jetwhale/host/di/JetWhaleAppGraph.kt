package com.kitakkun.jetwhale.host.di

import com.kitakkun.jetwhale.host.ApplicationLifecycleOwner
import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.drawer.ToolingScaffoldScreenContext
import com.kitakkun.jetwhale.host.model.AppearanceSettingsSubscriptionKey
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.LogCaptureService
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.ThemeSubscriptionKey
import com.kitakkun.jetwhale.host.plugin.PluginScreenContext
import com.kitakkun.jetwhale.host.settings.SettingsScreenContext
import com.kitakkun.jetwhale.host.settings.licenses.LicensesScreenContext
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import kotlinx.serialization.json.Json
import soil.query.SwrCachePlus
import soil.query.SwrCachePlusPolicy
import soil.query.SwrCacheScope
import soil.query.SwrClientPlus

@DependencyGraph(AppScope::class)
interface JetWhaleAppGraph :
    PluginScreenContext.Factory,
    SettingsScreenContext.Factory,
    ToolingScaffoldScreenContext.Factory,
    LicensesScreenContext.Factory,
    ScreenContext {
    val applicationLifecycleOwner: ApplicationLifecycleOwner
    val swrClient: SwrClientPlus

    val themeSubscriptionKey: ThemeSubscriptionKey
    val appearanceSettingsSubscriptionKey: AppearanceSettingsSubscriptionKey
    val debugWebSocketServer: DebugWebSocketServer
    val pluginComposeSceneService: PluginComposeSceneService
    val pluginInstancService: PluginInstanceService
    val logCaptureService: LogCaptureService
    val enabledPluginsRepository: EnabledPluginsRepository

    @Provides
    fun provideSwrClient(): SwrClientPlus = SwrCachePlus(SwrCachePlusPolicy(SwrCacheScope()))

    @Provides
    fun providesWebSocketPayloadJson(): Json {
        return JetWhaleJson
    }
}
