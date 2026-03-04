package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing plugin factories.
 */
interface PluginFactoryRepository {
    val loadedPluginsFlow: Flow<Map<String, LoadedPlugin>>
    val loadedPlugins: Map<String, LoadedPlugin>

    suspend fun loadPluginFactory(pluginJarPath: String)
    suspend fun unloadPlugin(pluginId: String)
}
