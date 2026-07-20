package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.model.SignPluginTrustRegistrySubscriptionKey
import com.kitakkun.jetwhale.host.model.TrustRegistrySigningState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.map
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultSignPluginTrustRegistrySubscriptionKey(
    private val pluginTrustService: PluginTrustService,
) : SignPluginTrustRegistrySubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("sign_plugin_trust_registry"),
    subscribe = {
        pluginTrustService.signingEnabledFlow.map { TrustRegistrySigningState(it) }
    },
)
