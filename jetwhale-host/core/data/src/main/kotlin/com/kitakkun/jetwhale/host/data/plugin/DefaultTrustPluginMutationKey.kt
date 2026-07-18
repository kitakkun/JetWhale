package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.model.TrustPluginMutationKey
import com.kitakkun.jetwhale.host.model.TrustPluginRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultTrustPluginMutationKey(
    private val pluginTrustService: PluginTrustService,
) : TrustPluginMutationKey by buildMutationKey(
    id = MutationId("trust_plugin"),
    mutate = { request: TrustPluginRequest ->
        pluginTrustService.trustAndLoad(request.jarPath)
    },
)
