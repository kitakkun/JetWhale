package com.kitakkun.jetwhale.host.plugin

import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKey
import com.kitakkun.jetwhale.host.model.PluginComposeSceneQueryKeyFactory
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject

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
) : ScreenContext {
    val pluginComposeSceneQueryKey: PluginComposeSceneQueryKey =
        pluginComposeSceneQueryKeyFactory.create(pluginId, sessionId)

    @AssistedFactory
    fun interface Factory {
        fun create(pluginId: String, sessionId: String): PluginScreenContext
    }
}
