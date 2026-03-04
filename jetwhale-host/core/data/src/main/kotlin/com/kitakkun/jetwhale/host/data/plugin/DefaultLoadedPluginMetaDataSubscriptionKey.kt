package com.kitakkun.jetwhale.host.data.plugin

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
) : LoadedPluginsMetaDataSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("default_loaded_plugin_meta_data_subscription_key"),
    subscribe = {
        pluginFactoryRepository.loadedPluginsFlow.map { pluginMap ->
            pluginMap.map { (_, plugin) ->
                PluginMetaData(
                    name = plugin.pluginName,
                    id = plugin.pluginId,
                    version = plugin.version,
                    activeIconResource = plugin.activeIconPath?.let {
                        val resource =
                            plugin.factory.javaClass.classLoader.getResource(it) ?: return@let null
                        PluginIconResource(resource)
                    },
                    inactiveIconResource = plugin.inactiveIconPath?.let {
                        val resource =
                            plugin.factory.javaClass.classLoader.getResource(it) ?: return@let null
                        PluginIconResource(resource)
                    },
                )
            }.toPersistentList()
        }
    },
)
