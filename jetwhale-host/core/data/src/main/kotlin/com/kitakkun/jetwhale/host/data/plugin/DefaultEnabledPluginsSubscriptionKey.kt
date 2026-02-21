package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultEnabledPluginsSubscriptionKey(
    private val enabledPluginsRepository: EnabledPluginsRepository,
) : EnabledPluginsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("enabled_plugins"),
    subscribe = {
        enabledPluginsRepository.enabledPluginIdsFlow
    }
)
