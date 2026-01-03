package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.flow.Flow

internal interface JetWhaleSocketClient {
    suspend fun sendMessage(pluginId: String, message: String)
    suspend fun openConnection(host: String, port: Int): Flow<String>
}
