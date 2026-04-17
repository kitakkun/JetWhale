package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing loaded plugins.
 */
interface PluginFactoryRepository {
    val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>>
    val loadedPlugins: Map<String, LoadedHostPlugin>

    suspend fun loadPlugin(pluginJarPath: String)
    suspend fun unloadPlugin(pluginId: String)
}
