package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HeadlessPluginsSubscriptionKey
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultHeadlessPluginsSubscriptionKey(
    private val pluginInstanceService: PluginInstanceService,
) : HeadlessPluginsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("headless_plugins"),
    subscribe = { pluginInstanceService.headlessPluginsFlow },
)
