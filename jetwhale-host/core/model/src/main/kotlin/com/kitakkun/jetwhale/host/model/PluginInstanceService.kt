package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawHostPlugin

interface PluginInstanceService {
    fun unloadPluginInstanceForSession(sessionId: String)
    suspend fun getOrPutPluginInstanceForSession(pluginId: String, sessionId: String, pluginFactory: JetWhaleHostPluginFactory): JetWhaleRawHostPlugin
}
