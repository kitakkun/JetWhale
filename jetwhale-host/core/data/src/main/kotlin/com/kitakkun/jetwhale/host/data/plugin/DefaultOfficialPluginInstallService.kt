package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.OfficialPlugin
import com.kitakkun.jetwhale.host.model.OfficialPluginInstallService
import com.kitakkun.jetwhale.host.model.PluginInstallJobService
import com.kitakkun.jetwhale.host.model.PluginInstallRequest
import com.kitakkun.jetwhale.host.model.PluginInstallStatus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultOfficialPluginInstallService(
    private val pluginInstallJobService: PluginInstallJobService,
) : OfficialPluginInstallService {
    override suspend fun install(plugin: OfficialPlugin) {
        // Through the same queue as the settings screen, so an agent's install shows up there and
        // joins one the user already started instead of racing it.
        val outcome = pluginInstallJobService.install(PluginInstallRequest.Official(plugin))
        if (outcome is PluginInstallStatus.Failed) throw PluginInstallationException(outcome.reason)
    }
}
