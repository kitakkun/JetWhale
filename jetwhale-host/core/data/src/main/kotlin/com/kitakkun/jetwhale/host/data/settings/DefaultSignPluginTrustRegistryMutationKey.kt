package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.model.SignPluginTrustRegistryMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<SignPluginTrustRegistryMutationKey>())
@Inject
class DefaultSignPluginTrustRegistryMutationKey(
    private val pluginTrustService: PluginTrustService,
) : SignPluginTrustRegistryMutationKey,
    MutationKey<Unit, Boolean> by buildMutationKey(
        id = MutationId("sign_plugin_trust_registry"),
        // Route through the service so enabling signing also re-signs the existing registry (one
        // credential-store prompt), rather than only flipping the setting.
        mutate = { enabled: Boolean ->
            pluginTrustService.setSigningEnabled(enabled)
        },
    )
