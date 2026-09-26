package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.ui.InternalComposeUiApi
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKeyFactory
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import soil.query.QueryContentCacheable
import soil.query.QueryId
import soil.query.buildQueryKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginComposeSceneQueryKeyFactory(
    private val pluginComposeSceneService: PluginComposeSceneService,
    private val pluginInstanceService: PluginInstanceService,
) : PluginComposeSceneQueryKeyFactory {
    @OptIn(InternalComposeUiApi::class)
    override fun create(pluginId: String, sessionId: String): PluginComposeSceneQueryKey = object : PluginComposeSceneQueryKey by buildQueryKey(
        id = QueryId("PluginComposeScene:$pluginId:$sessionId"),
        // A screen can be opened before its instance exists (a no-app plugin at startup, a plugin
        // just switched on) or while it is being replaced, so the scene waits for an instance and
        // keeps waiting if the one it saw is gone by the time the scene is built.
        fetch = {
            var scene: PluginComposeScene? = null
            while (scene == null) {
                pluginInstanceService.pluginInstanceFlow(pluginId, sessionId).filterNotNull().first()
                scene = pluginComposeSceneService.getOrCreatePluginScene(pluginId = pluginId, sessionId = sessionId)
            }
            scene
        },
    ) {
        override val contentCacheable: QueryContentCacheable<PluginComposeScene>
            // Disable caching to avoid issues with ComposeScene re-use when session is resumed
            get() = { false }
    }
}
