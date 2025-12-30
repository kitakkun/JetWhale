package com.kitakkun.jetwhale.debugger.host.data.plugin

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import com.kitakkun.jetwhale.debugger.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.debugger.host.model.PluginComposeSceneRepository
import com.kitakkun.jetwhale.debugger.host.model.PluginIdQualifier
import com.kitakkun.jetwhale.debugger.host.model.SessionIdQualifier
import com.kitakkun.jetwhale.debugger.protocol.InternalJetWhaleApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.QueryContentCacheable
import soil.query.QueryId
import soil.query.buildQueryKey

@OptIn(InternalComposeUiApi::class, InternalJetWhaleApi::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginComposeSceneQueryKey(
    @param:PluginIdQualifier private val pluginId: String,
    @param:SessionIdQualifier private val sessionId: String,
    private val density: Density,
    private val pluginComposeSceneRepository: PluginComposeSceneRepository,
) : PluginComposeSceneQueryKey by buildQueryKey(
    id = QueryId("PluginComposeScene:$pluginId:$sessionId"),
    fetch = {
        pluginComposeSceneRepository.getOrCreatePluginScene(
            pluginId = pluginId,
            sessionId = sessionId,
            density = density,
        )
    }
) {
    override val contentCacheable: QueryContentCacheable<ComposeScene>
        // Disable caching to avoid issues with ComposeScene re-use when session is resumed
        get() = { false }
}
