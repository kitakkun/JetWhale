package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.CancelPluginInstallMutationKey
import com.kitakkun.jetwhale.host.model.PluginInstallJobService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<CancelPluginInstallMutationKey>())
@Inject
class DefaultCancelPluginInstallMutationKey(
    private val pluginInstallJobService: PluginInstallJobService,
) : CancelPluginInstallMutationKey,
    MutationKey<Unit, String> by buildMutationKey(
        id = MutationId("cancel_plugin_install"),
        mutate = { jobId: String -> pluginInstallJobService.cancel(jobId) },
    )
