package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.debugger.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.debugger.host.sdk.JetWhaleHostPluginFactory
import kotlinx.coroutines.flow.Flow

interface PluginRepository {
    val loadedPluginFactoriesFlow: Flow<Map<String, JetWhaleHostPluginFactory>>

    suspend fun loadPluginFactory(pluginJarPath: String)
    suspend fun unloadPlugin(pluginId: String)

    suspend fun getOrPutPluginInstanceForSession(
        pluginId: String,
        sessionId: String,
    ): JetWhaleHostPlugin

    fun unloadPluginInstanceForSession(sessionId: String)
}
