package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginInstallJobService
import com.kitakkun.jetwhale.host.model.PluginInstallJobsSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultPluginInstallJobsSubscriptionKey(
    private val pluginInstallJobService: PluginInstallJobService,
) : PluginInstallJobsSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("plugin_install_jobs"),
    subscribe = { pluginInstallJobService.jobsFlow },
)
