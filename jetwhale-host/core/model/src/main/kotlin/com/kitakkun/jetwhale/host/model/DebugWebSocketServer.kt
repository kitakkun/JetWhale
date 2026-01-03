package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DebugWebSocketServer {
    val statusFlow: StateFlow<DebugWebSocketServerStatus>
    val sessionClosedFlow: Flow<String>

    suspend fun start(host: String, port: Int)
    suspend fun stop()
    suspend fun sendMessage(pluginId: String, sessionId: String, message: String): String?
}
