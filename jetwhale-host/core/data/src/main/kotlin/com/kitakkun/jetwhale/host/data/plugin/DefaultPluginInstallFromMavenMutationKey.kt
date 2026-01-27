package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.PluginInstallFromMavenMutationKey
import com.kitakkun.jetwhale.host.model.PluginRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultPluginInstallFromMavenMutationKey(
    private val pluginRepository: PluginRepository,
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val mavenArtifactResolver: MavenArtifactResolver,
) : PluginInstallFromMavenMutationKey by buildMutationKey(
    id = MutationId("pluginInstallFromMaven"),
    mutate = { coordinates: MavenCoordinates ->
        appDataDirectoryProvider.createAppDataDirectoriesIfNeeded()
        val pluginDir = appDataDirectoryProvider.getPluginDirectory()
        val downloadedJarPath = mavenArtifactResolver.downloadJar(coordinates, pluginDir)
        pluginRepository.loadPluginFactory(downloadedJarPath)
    }
)
