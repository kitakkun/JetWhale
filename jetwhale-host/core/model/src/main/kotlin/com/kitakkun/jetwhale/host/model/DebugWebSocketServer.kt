package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DebugWebSocketServer {
    val statusFlow: StateFlow<DebugWebSocketServerStatus>
    val sessionClosedFlow: Flow<String>
    val serverStoppedFlow: Flow<Unit>

    suspend fun start(host: String, port: Int, wssPort: Int?)
    suspend fun stop()
}
