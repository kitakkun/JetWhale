package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.OfficialPlugin
import com.kitakkun.jetwhale.host.model.OfficialPluginInstallService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultOfficialPluginInstallService(
    private val mavenPluginInstallService: MavenPluginInstallService,
    private val hostVersionInfo: HostVersionInfo,
) : OfficialPluginInstallService {
    override suspend fun install(plugin: OfficialPlugin) {
        mavenPluginInstallService.installFirstAvailable(plugin.installCandidatesFor(hostVersionInfo))
    }
}
