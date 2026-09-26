package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.InstalledPluginVersion
import com.kitakkun.jetwhale.host.model.LoadedPluginsMetaDataSubscriptionKey
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginIconResource
import com.kitakkun.jetwhale.host.model.PluginMetaData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.map
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultLoadedPluginMetaDataSubscriptionKey(
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
) : LoadedPluginsMetaDataSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("default_loaded_plugin_meta_data_subscription_key"),
    subscribe = {
        pluginFactoryRepository.loadedPluginVersionsFlow.map { versionsById ->
            versionsById.values.mapNotNull { versions ->
                val loaded = versions.firstOrNull() ?: return@mapNotNull null
                val classLoader = loaded.factory.javaClass.classLoader
                PluginMetaData(
                    name = loaded.manifest.pluginName,
                    id = loaded.manifest.pluginId,
                    version = loaded.manifest.version,
                    installedVersions = versions.map {
                        InstalledPluginVersion(
                            version = it.manifest.version,
                            jarPath = it.jarPath,
                            removable = appDataDirectoryProvider.isManagedPluginJarPath(it.jarPath),
                        )
                    },
                    requiresAgent = loaded.manifest.requiresAgent,
                    activeIconResource = loaded.manifest.icon?.activePath?.let {
                        val resource = classLoader.getResource(it) ?: return@let null
                        PluginIconResource(resource)
                    },
                    inactiveIconResource = loaded.manifest.icon?.inactivePath?.let {
                        val resource = classLoader.getResource(it) ?: return@let null
                        PluginIconResource(resource)
                    },
                )
            }.toPersistentList()
        }
    },
)
