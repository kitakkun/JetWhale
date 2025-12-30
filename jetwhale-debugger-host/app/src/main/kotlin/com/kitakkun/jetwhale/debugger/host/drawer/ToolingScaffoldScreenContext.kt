package com.kitakkun.jetwhale.debugger.host.drawer

import com.kitakkun.jetwhale.debugger.host.architecture.ScreenContext
import com.kitakkun.jetwhale.debugger.host.model.DebugSessionsSubscriptionKey
import com.kitakkun.jetwhale.debugger.host.model.LoadedPluginsMetaDataSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension

@ContributesTo(AppScope::class)
@GraphExtension
interface ToolingScaffoldScreenContext : ScreenContext {
    val loadedPluginsMetaDataSubscriptionKey: LoadedPluginsMetaDataSubscriptionKey
    val debugSessionsSubscriptionKey: DebugSessionsSubscriptionKey

    @GraphExtension.Factory
    fun interface Factory {
        fun createToolingScaffoldScreenContext(): ToolingScaffoldScreenContext
    }
}