package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing plugin factories.
 */
interface PluginFactoryRepository {
    val loadedPluginFactoriesFlow: Flow<Map<String, JetWhaleHostPluginFactory>>
    val loadedPluginFactories: Map<String, JetWhaleHostPluginFactory>

    suspend fun loadPluginFactory(pluginJarPath: String)
    suspend fun unloadPlugin(pluginId: String)
}
