package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.model.RemovePluginJarMutationKey
import com.kitakkun.jetwhale.host.model.RemovePluginJarRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultRemovePluginJarMutationKey(
    private val pluginTrustService: PluginTrustService,
) : RemovePluginJarMutationKey by buildMutationKey(
    id = MutationId("remove_plugin_jar"),
    mutate = { request: RemovePluginJarRequest ->
        pluginTrustService.removePluginJar(request.jarPath)
    },
)
