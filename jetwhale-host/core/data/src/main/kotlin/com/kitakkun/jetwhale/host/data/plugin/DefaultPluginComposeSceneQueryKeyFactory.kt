package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.ui.InternalComposeUiApi
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKeyFactory
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.QueryContentCacheable
import soil.query.QueryId
import soil.query.buildQueryKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginComposeSceneQueryKeyFactory(
    private val pluginComposeSceneService: PluginComposeSceneService,
) : PluginComposeSceneQueryKeyFactory {
    @OptIn(InternalComposeUiApi::class)
    override fun create(pluginId: String, sessionId: String): PluginComposeSceneQueryKey = object : PluginComposeSceneQueryKey by buildQueryKey(
        id = QueryId("PluginComposeScene:$pluginId:$sessionId"),
        fetch = {
            pluginComposeSceneService.getOrCreatePluginScene(
                pluginId = pluginId,
                sessionId = sessionId,
            )
        },
    ) {
        override val contentCacheable: QueryContentCacheable<PluginComposeScene>
            // Not cacheable: a cached ComposeScene must not be reused when a session resumes.
            get() = { false }
    }
}
