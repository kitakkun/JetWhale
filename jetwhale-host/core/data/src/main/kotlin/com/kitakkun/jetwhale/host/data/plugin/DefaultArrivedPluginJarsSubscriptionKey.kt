package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.ArrivedPluginJars
import com.kitakkun.jetwhale.host.model.ArrivedPluginJarsSubscriptionKey
import com.kitakkun.jetwhale.host.model.PluginTrustService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.map
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultArrivedPluginJarsSubscriptionKey(
    private val pluginTrustService: PluginTrustService,
) : ArrivedPluginJarsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("default_arrived_plugin_jars_subscription_key"),
    subscribe = {
        pluginTrustService.arrivedJarsFlow.map { ArrivedPluginJars(it.toPersistentList()) }
    },
)
