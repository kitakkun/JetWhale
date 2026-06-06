package com.kitakkun.jetwhale.host.plugin

import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKeyFactory
import com.kitakkun.jetwhale.host.model.PluginHotReloadService
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/**
 * Screen context for a plugin instance. The plugin/session ids arrive as NavKey arguments, so
 * this is created via assisted injection; the compose-scene query is keyed by those ids and
 * built by the injected [PluginComposeSceneQueryKeyFactory].
 */
@AssistedInject
class PluginScreenContext(
    @Assisted val pluginId: String,
    @Assisted val sessionId: String,
    pluginComposeSceneQueryKeyFactory: PluginComposeSceneQueryKeyFactory,
    pluginHotReloadService: PluginHotReloadService,
) : ScreenContext {
    val pluginComposeSceneQueryKey: PluginComposeSceneQueryKey =
        pluginComposeSceneQueryKeyFactory.create(pluginId, sessionId)

    /**
     * Emits whenever this screen's plugin is hot-reloaded, so the screen can re-create its compose
     * scene from the freshly loaded code. Inert in production (no dev plugins directory configured).
     */
    val pluginReloadedFlow: Flow<String> = pluginHotReloadService.pluginReloadedFlow
        .filter { reloadedPluginId -> reloadedPluginId == pluginId }

    @AssistedFactory
    fun interface Factory {
        fun create(pluginId: String, sessionId: String): PluginScreenContext
    }
}
