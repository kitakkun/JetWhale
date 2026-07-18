package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.OfficialPlugin
import com.kitakkun.jetwhale.host.model.OfficialPluginInstallMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultOfficialPluginInstallMutationKey(
    private val mavenPluginInstallService: MavenPluginInstallService,
    private val hostVersionInfo: HostVersionInfo,
) : OfficialPluginInstallMutationKey by buildMutationKey(
    id = MutationId("officialPluginInstall"),
    mutate = { plugin: OfficialPlugin ->
        mavenPluginInstallService.installFirstAvailable(plugin.installCandidatesFor(hostVersionInfo))
    },
)
