package com.kitakkun.jetwhale.host.plugin

import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.PluginJarSwapService
import com.kitakkun.jetwhale.host.model.PluginScreenStateSubscriptionKey
import com.kitakkun.jetwhale.host.model.PluginScreenStateSubscriptionKeyFactory
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/**
 * Screen context for a plugin instance. The plugin/session ids arrive as NavKey arguments, so
 * this is created via assisted injection; the screen-state subscription is keyed by those ids and
 * built by the injected [PluginScreenStateSubscriptionKeyFactory].
 */
@AssistedInject
class PluginScreenContext(
    @Assisted val pluginId: String,
    @Assisted val sessionId: String,
    pluginScreenStateSubscriptionKeyFactory: PluginScreenStateSubscriptionKeyFactory,
    pluginJarSwapService: PluginJarSwapService,
) : ScreenContext {
    val pluginScreenStateSubscriptionKey: PluginScreenStateSubscriptionKey =
        pluginScreenStateSubscriptionKeyFactory.create(pluginId, sessionId)

    /**
     * Emits whenever this screen's plugin gets new code — a dev hot reload, or an approved update of
     * its jar — so the screen can say so.
     */
    val pluginReloadedFlow: Flow<String> = pluginJarSwapService.pluginReloadedFlow
        .filter { reloadedPluginId -> reloadedPluginId == pluginId }

    @AssistedFactory
    fun interface Factory {
        fun create(pluginId: String, sessionId: String): PluginScreenContext
    }
}
