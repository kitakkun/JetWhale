package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstallMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultPluginInstallMutationKey(
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
) : PluginInstallMutationKey by buildMutationKey(
    id = MutationId("pluginInstall"),
    mutate = { jarUrlString: String ->
        appDataDirectoryProvider.createAppDataDirectoriesIfNeeded()
        val copiedJarFilePath = appDataDirectoryProvider.copyJarFileToAppDataDirectory(jarUrlString)
        pluginFactoryRepository.loadPluginFactory(copiedJarFilePath)
    },
)
