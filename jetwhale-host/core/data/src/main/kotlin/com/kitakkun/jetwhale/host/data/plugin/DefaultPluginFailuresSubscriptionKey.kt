package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginFailuresSubscriptionKey
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultPluginFailuresSubscriptionKey(
    private val pluginInstanceService: PluginInstanceService,
) : PluginFailuresSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("plugin_failures"),
    subscribe = { pluginInstanceService.pluginFailuresFlow },
)
