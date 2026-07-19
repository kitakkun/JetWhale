package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
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
    private val settingsRepository: DebuggerSettingsRepository,
) : SignPluginTrustRegistryMutationKey,
    MutationKey<Unit, Boolean> by buildMutationKey(
        id = MutationId("sign_plugin_trust_registry"),
        mutate = { enabled: Boolean ->
            settingsRepository.updateSignPluginTrustRegistry(enabled)
        },
    )
