package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawHostPlugin
import kotlinx.coroutines.flow.Flow

interface PluginRepository {
    val loadedPluginFactoriesFlow: Flow<Map<String, JetWhaleHostPluginFactory>>
    val loadedPluginFactories: Map<String, JetWhaleHostPluginFactory>

    suspend fun loadPluginFactory(pluginJarPath: String)
    suspend fun unloadPlugin(pluginId: String)

    suspend fun getOrPutPluginInstanceForSession(
        pluginId: String,
        sessionId: String,
    ): JetWhaleRawHostPlugin

    fun unloadPluginInstanceForSession(sessionId: String)
}
