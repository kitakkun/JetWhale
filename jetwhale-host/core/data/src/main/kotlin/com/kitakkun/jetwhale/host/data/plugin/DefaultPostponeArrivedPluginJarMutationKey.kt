package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.model.PostponeArrivedPluginJarMutationKey
import com.kitakkun.jetwhale.host.model.PostponeArrivedPluginJarRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultPostponeArrivedPluginJarMutationKey(
    private val pluginTrustService: PluginTrustService,
) : PostponeArrivedPluginJarMutationKey by buildMutationKey(
    id = MutationId("postpone_arrived_plugin_jar"),
    mutate = { request: PostponeArrivedPluginJarRequest ->
        pluginTrustService.postponeArrivedJar(request.jarPath)
    },
)
