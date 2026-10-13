package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginInstallJobService
import com.kitakkun.jetwhale.host.model.PluginInstallRequest
import com.kitakkun.jetwhale.host.model.StartPluginInstallMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultStartPluginInstallMutationKey(
    private val pluginInstallJobService: PluginInstallJobService,
) : StartPluginInstallMutationKey by buildMutationKey(
    id = MutationId("start_plugin_install"),
    mutate = { request: PluginInstallRequest ->
        pluginInstallJobService.enqueue(request)
    },
)
