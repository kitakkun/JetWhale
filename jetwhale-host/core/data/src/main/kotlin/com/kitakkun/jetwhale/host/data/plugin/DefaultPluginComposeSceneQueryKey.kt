package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.scene.ComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginIdQualifier
import com.kitakkun.jetwhale.host.model.SessionIdQualifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.QueryContentCacheable
import soil.query.QueryId
import soil.query.buildQueryKey

@OptIn(InternalComposeUiApi::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginComposeSceneQueryKey(
    @param:PluginIdQualifier private val pluginId: String,
    @param:SessionIdQualifier private val sessionId: String,
    private val pluginComposeSceneService: PluginComposeSceneService,
) : PluginComposeSceneQueryKey by buildQueryKey(
    id = QueryId("PluginComposeScene:$pluginId:$sessionId"),
    fetch = {
        pluginComposeSceneService.getOrCreatePluginScene(
            pluginId = pluginId,
            sessionId = sessionId,
        )
    }
) {
    override val contentCacheable: QueryContentCacheable<ComposeScene>
        // Disable caching to avoid issues with ComposeScene re-use when session is resumed
        get() = { false }
}
