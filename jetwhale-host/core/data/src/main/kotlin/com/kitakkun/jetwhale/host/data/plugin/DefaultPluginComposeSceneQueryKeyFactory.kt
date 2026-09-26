package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.ui.InternalComposeUiApi
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKeyFactory
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginInstanceState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import soil.query.QueryContentCacheable
import soil.query.QueryId
import soil.query.buildQueryKey
import kotlin.time.Duration.Companion.seconds

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
        // waits again for a different one if the one it saw is gone by the time the scene is built.
        // An instance that failed to start, or never comes, ends the wait with an error instead.
        fetch = {
            withTimeoutOrNull(PLUGIN_START_TIMEOUT) {
                var sceneBuiltFor: PluginInstanceState? = null
                var scene: PluginComposeScene? = null
                while (scene == null) {
                    val state = pluginInstanceService.pluginInstanceStateFlow(pluginId, sessionId)
                        .first { it != PluginInstanceState.Absent && it != sceneBuiltFor }
                    if (state is PluginInstanceState.FailedToStart) {
                        throw IllegalStateException("The plugin failed to start: ${state.cause.message}", state.cause)
                    }
                    sceneBuiltFor = state
                    scene = pluginComposeSceneService.getOrCreatePluginScene(pluginId = pluginId, sessionId = sessionId)
                }
                scene
            } ?: error("The plugin didn't start within $PLUGIN_START_TIMEOUT: it is not installed for this session, or its startup is stuck.")
        },
    ) {
        override val contentCacheable: QueryContentCacheable<PluginComposeScene>
            // Disable caching to avoid issues with ComposeScene re-use when session is resumed
            get() = { false }
    }
}

private val PLUGIN_START_TIMEOUT = 10.seconds
