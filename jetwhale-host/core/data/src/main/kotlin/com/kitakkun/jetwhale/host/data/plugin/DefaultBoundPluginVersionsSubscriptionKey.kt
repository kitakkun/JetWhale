package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.BoundPluginVersionsSubscriptionKey
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultBoundPluginVersionsSubscriptionKey(
    private val pluginInstanceService: PluginInstanceService,
) : BoundPluginVersionsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("bound_plugin_versions"),
    subscribe = { pluginInstanceService.boundVersionsFlow },
)
