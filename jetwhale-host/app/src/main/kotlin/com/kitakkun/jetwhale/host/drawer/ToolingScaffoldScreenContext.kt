package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.DebugSessionsSubscriptionKey
import com.kitakkun.jetwhale.host.model.EnabledPluginsSubscriptionKey
import com.kitakkun.jetwhale.host.model.LoadedPluginsMetaDataSubscriptionKey
import com.kitakkun.jetwhale.host.model.SetPluginEnabledMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension

@ContributesTo(AppScope::class)
@GraphExtension
interface ToolingScaffoldScreenContext : ScreenContext {
    val loadedPluginsMetaDataSubscriptionKey: LoadedPluginsMetaDataSubscriptionKey
    val debugSessionsSubscriptionKey: DebugSessionsSubscriptionKey
    val enabledPluginsSubscriptionKey: EnabledPluginsSubscriptionKey
    val setPluginEnabledMutationKey: SetPluginEnabledMutationKey

    @GraphExtension.Factory
    fun interface Factory {
        fun createToolingScaffoldScreenContext(): ToolingScaffoldScreenContext
    }
}
