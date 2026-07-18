package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.PluginInstallFromMavenMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultPluginInstallFromMavenMutationKey(
    private val mavenPluginInstallService: MavenPluginInstallService,
) : PluginInstallFromMavenMutationKey by buildMutationKey(
    id = MutationId("pluginInstallFromMaven"),
    mutate = { coordinates: MavenCoordinates ->
        mavenPluginInstallService.installFirstAvailable(listOf(coordinates))
    },
)
