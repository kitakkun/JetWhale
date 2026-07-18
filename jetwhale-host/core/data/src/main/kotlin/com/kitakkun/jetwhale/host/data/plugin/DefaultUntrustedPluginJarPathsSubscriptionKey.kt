package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.model.UntrustedPluginJarPathsSubscriptionKey
import com.kitakkun.jetwhale.host.model.UntrustedPluginJars
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.map
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultUntrustedPluginJarPathsSubscriptionKey(
    private val pluginTrustService: PluginTrustService,
) : UntrustedPluginJarPathsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("default_untrusted_plugin_jar_paths_subscription_key"),
    subscribe = {
        pluginTrustService.untrustedJarPathsFlow.map { UntrustedPluginJars(it.toPersistentList()) }
    },
)
