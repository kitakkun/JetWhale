package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.FailedPluginJarPathsSubscriptionKey
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.map
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultFailedPluginJarPathsSubscriptionKey(
    private val pluginFactoryRepository: PluginFactoryRepository,
) : FailedPluginJarPathsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("default_failed_plugin_jar_paths_subscription_key"),
    subscribe = {
        pluginFactoryRepository.failedJarPathsFlow.map { it.toPersistentList() }
    },
)
