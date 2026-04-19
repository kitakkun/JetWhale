package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface DebugWebSocketServer {
    val sessionClosedFlow: Flow<String>
    val serverStoppedFlow: Flow<Unit>

    suspend fun start(host: String, port: Int)
    suspend fun stop()
    suspend fun sendMethod(pluginId: String, sessionId: String, payload: String): String?
    suspend fun sendEvent(pluginId: String, sessionId: String, payload: String)
    fun getCoroutineScopeForSession(sessionId: String): CoroutineScope
}
