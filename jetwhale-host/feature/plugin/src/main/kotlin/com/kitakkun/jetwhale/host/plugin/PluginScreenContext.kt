package com.kitakkun.jetwhale.host.plugin

import androidx.compose.ui.InternalComposeUiApi
import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.host.model.PluginIdQualifier
import com.kitakkun.jetwhale.host.model.PluginScope
import com.kitakkun.jetwhale.host.model.SessionIdQualifier
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
        ): PluginScreenContext
    }
}
