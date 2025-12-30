package com.kitakkun.jetwhale.debugger.host.data.plugin

import com.kitakkun.jetwhale.debugger.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.debugger.host.model.PluginInstallMutationKey
import com.kitakkun.jetwhale.debugger.host.model.PluginRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultPluginInstallMutationKey(
    private val pluginRepository: PluginRepository,
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
) : PluginInstallMutationKey by buildMutationKey(
    id = MutationId("pluginInstall"),
    mutate = { jarUrlString: String ->
        appDataDirectoryProvider.createAppDataDirectoriesIfNeeded()
        val copiedJarFilePath = appDataDirectoryProvider.copyJarFileToAppDataDirectory(jarUrlString)
        pluginRepository.loadPluginFactory(copiedJarFilePath)
    }
)
