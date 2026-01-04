package com.kitakkun.jetwhale.host.data.server

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.util.logging.Logger

interface ServerSessionNegotiator {
    context(logger: Logger)
    suspend fun DefaultWebSocketServerSession.negotiate(): ServerSessionNegotiationResult
}
