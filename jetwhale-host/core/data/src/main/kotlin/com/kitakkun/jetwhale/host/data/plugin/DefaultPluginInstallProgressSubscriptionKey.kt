package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginInstallProgressSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultPluginInstallProgressSubscriptionKey(
    private val pluginInstallProgressRepository: PluginInstallProgressRepository,
) : PluginInstallProgressSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("default_plugin_install_progress_subscription_key"),
    subscribe = { pluginInstallProgressRepository.progressFlow },
)
