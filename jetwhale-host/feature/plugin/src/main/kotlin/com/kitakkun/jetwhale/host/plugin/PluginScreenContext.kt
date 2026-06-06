package com.kitakkun.jetwhale.host.plugin

import androidx.compose.ui.InternalComposeUiApi
import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import soil.query.QueryContentCacheable
import soil.query.QueryId
import soil.query.buildQueryKey

/**
 * Screen context for a plugin instance. The plugin/session ids arrive as NavKey arguments,
 * so this is created via assisted injection. The compose-scene query is keyed by those ids
 * and is therefore owned here rather than provided as an app-scoped binding.
 */
@OptIn(InternalComposeUiApi::class)
@AssistedInject
class PluginScreenContext(
    @Assisted val pluginId: String,
    @Assisted val sessionId: String,
    private val pluginComposeSceneService: PluginComposeSceneService,
) : ScreenContext {
    val pluginComposeSceneQueryKey: PluginComposeSceneQueryKey =
        object : PluginComposeSceneQueryKey by buildQueryKey(
            id = QueryId("PluginComposeScene:$pluginId:$sessionId"),
            fetch = {
                pluginComposeSceneService.getOrCreatePluginScene(
                    pluginId = pluginId,
                    sessionId = sessionId,
                )
            },
        ) {
            override val contentCacheable: QueryContentCacheable<PluginComposeScene>
                // Disable caching to avoid issues with ComposeScene re-use when session is resumed
                get() = { false }
        }

    @AssistedFactory
    fun interface Factory {
        fun create(pluginId: String, sessionId: String): PluginScreenContext
    }
}
