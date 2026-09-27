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
import soil.query.buildSubscriptionKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginScreenStateSubscriptionKeyFactory(
    private val pluginInstanceService: PluginInstanceService,
) : PluginScreenStateSubscriptionKeyFactory {
    override fun create(pluginId: String, sessionId: String): PluginScreenStateSubscriptionKey = object : PluginScreenStateSubscriptionKey by buildSubscriptionKey(
        id = SubscriptionId("PluginScreenState:$pluginId:$sessionId"),
        subscribe = { pluginInstanceService.pluginScreenStateFlow(pluginId, sessionId) },
    ) {
        // A cached state would hand a screen opened later the scene of an instance that may be gone.
        override val contentCacheable: SubscriptionContentCacheable<PluginScreenState>
            get() = { false }
    }
}
