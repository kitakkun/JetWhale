package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

interface DebugWebSocketServer {
    val sessionClosedFlow: Flow<String>
    val serverStoppedFlow: Flow<Unit>

    suspend fun start(host: String, port: Int, wssPort: Int?)
    suspend fun stop()
}
