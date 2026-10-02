package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginScreenState
import com.kitakkun.jetwhale.host.model.PluginScreenStateSubscriptionKey
import com.kitakkun.jetwhale.host.model.PluginScreenStateSubscriptionKeyFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionContentCacheable
import soil.query.SubscriptionId
import soil.query.SubscriptionOptionsOverride
import soil.query.buildSubscriptionKey
import soil.query.copy
import kotlin.time.Duration

@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginScreenStateSubscriptionKeyFactory(
    private val pluginInstanceService: PluginInstanceService,
) : PluginScreenStateSubscriptionKeyFactory {
    override fun create(pluginId: String, sessionId: String): PluginScreenStateSubscriptionKey = object : PluginScreenStateSubscriptionKey by buildSubscriptionKey(
        id = SubscriptionId("PluginScreenState:$pluginId:$sessionId"),
        subscribe = { pluginInstanceService.pluginScreenStateFlow(pluginId, sessionId) },
    ) {
        // soil keeps a subscription's source running for the keep-alive time after its last screen
        // closes, but drops what it emits meanwhile: a screen reopened within that time would show
        // the state from before, possibly a disposed scene, until the instance changes again.
        override fun onConfigureOptions(): SubscriptionOptionsOverride = { it.copy(keepAliveTime = Duration.ZERO) }

        // A cached state is shown to the next screen before its subscription delivers, and may hold
        // a disposed scene.
        override val contentCacheable: SubscriptionContentCacheable<PluginScreenState>
            get() = { false }
    }
}
