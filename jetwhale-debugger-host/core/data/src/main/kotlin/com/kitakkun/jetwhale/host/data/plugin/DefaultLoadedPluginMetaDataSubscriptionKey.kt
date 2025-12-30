package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.LoadedPluginsMetaDataSubscriptionKey
import com.kitakkun.jetwhale.host.model.PluginIconResource
import com.kitakkun.jetwhale.host.model.PluginMetaData
import com.kitakkun.jetwhale.host.model.PluginRepository
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
    private val pluginRepository: PluginRepository,
) : LoadedPluginsMetaDataSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("default_loaded_plugin_meta_data_subscription_key"),
    subscribe = {
        pluginRepository.loadedPluginFactoriesFlow.map { pluginMap ->
            pluginMap.map { (_, plugin) ->
                PluginMetaData(
                    name = plugin.meta.pluginName,
                    id = plugin.meta.pluginId,
                    version = plugin.meta.version,
                    activeIconResource = plugin.icon.activeIconPath?.let {
                        val resource =
                            plugin.javaClass.classLoader.getResource(it) ?: return@let null
                        PluginIconResource(resource)
                    },
                    inactiveIconResource = plugin.icon.activeIconPath?.let {
                        val resource =
                            plugin.javaClass.classLoader.getResource(it) ?: return@let null
                        PluginIconResource(resource)
                    },
                )
            }.toPersistentList()
        }
    }
)
