package com.kitakkun.jetwhale.debugger.host.di

import com.kitakkun.jetwhale.debugger.host.ApplicationLifecycleOwner
import com.kitakkun.jetwhale.debugger.host.architecture.ScreenContext
import com.kitakkun.jetwhale.debugger.host.drawer.ToolingScaffoldScreenContext
import com.kitakkun.jetwhale.debugger.host.model.AppearanceSettingsSubscriptionKey
import com.kitakkun.jetwhale.debugger.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.debugger.host.model.PluginComposeSceneRepository
import com.kitakkun.jetwhale.debugger.host.model.ThemeSubscriptionKey
import com.kitakkun.jetwhale.debugger.host.plugin.PluginScreenContext
import com.kitakkun.jetwhale.debugger.host.settings.SettingsScreenContext
import com.kitakkun.jetwhale.debugger.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.debugger.protocol.serialization.JetWhaleJson
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
    ScreenContext {
    val applicationLifecycleOwner: ApplicationLifecycleOwner
    val swrClient: SwrClientPlus

    val themeSubscriptionKey: ThemeSubscriptionKey
    val appearanceSettingsSubscriptionKey: AppearanceSettingsSubscriptionKey
    val debugWebSocketServer: DebugWebSocketServer
    val pluginComposeSceneRepository: PluginComposeSceneRepository

    @Provides
    fun provideSwrClient(): SwrClientPlus = SwrCachePlus(SwrCachePlusPolicy(SwrCacheScope()))

    @OptIn(InternalJetWhaleApi::class)
    @Provides
    fun providesWebSocketPayloadJson(): Json {
        return JetWhaleJson
    }
}
