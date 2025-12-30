package com.kitakkun.jetwhale.debugger.host.plugin

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.unit.Density
import com.kitakkun.jetwhale.debugger.host.architecture.ScreenContext
import com.kitakkun.jetwhale.debugger.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.debugger.host.model.PluginIdQualifier
import com.kitakkun.jetwhale.debugger.host.model.PluginScope
import com.kitakkun.jetwhale.debugger.host.model.SessionIdQualifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

@GraphExtension(PluginScope::class)
interface PluginScreenContext : ScreenContext {
    @OptIn(InternalComposeUiApi::class)
    val pluginComposeSceneQueryKey: PluginComposeSceneQueryKey

    @PluginIdQualifier
    val pluginId: String

    @SessionIdQualifier
    val sessionId: String

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createPluginScreenContext(
            @PluginIdQualifier @Provides pluginId: String,
            @SessionIdQualifier @Provides sessionId: String,
            @Provides density: Density,
        ): PluginScreenContext
    }
}
