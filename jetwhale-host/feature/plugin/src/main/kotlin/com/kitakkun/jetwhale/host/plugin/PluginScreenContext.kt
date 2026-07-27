package com.kitakkun.jetwhale.host.plugin

import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKeyFactory
import com.kitakkun.jetwhale.host.model.PluginHotReloadService
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
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
    private val pluginInstanceService: PluginInstanceService,
    // Injects the same host environment (theme, language, Soil) into a web plugin's Content that the
    // off-screen compose-scene path injects for pure-Compose plugins.
    val pluginBridgeProvider: DynamicPluginBridgeProvider,
) : ScreenContext {
    val pluginComposeSceneQueryKey: PluginComposeSceneQueryKey =
        pluginComposeSceneQueryKeyFactory.create(pluginId, sessionId)

    /**
     * The live plugin instance for this screen, or `null` if it is not initialized yet. Used to pick
     * the rendering path: a web plugin (`JetWhaleWebHostPluginUi`) renders windowed, everything else
     * goes through the off-screen compose scene.
     */
    fun resolvePluginInstance(): JetWhaleHostPlugin? =
        pluginInstanceService.getPluginInstanceForSession(pluginId, sessionId)

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
