package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface DebugWebSocketServer {
    val sessionClosedFlow: Flow<String>

    suspend fun start(host: String, port: Int)
    suspend fun stop()
    suspend fun sendMethod(pluginId: String, sessionId: String, payload: String): String?
    fun getCoroutineScopeForSession(sessionId: String): CoroutineScope
}
