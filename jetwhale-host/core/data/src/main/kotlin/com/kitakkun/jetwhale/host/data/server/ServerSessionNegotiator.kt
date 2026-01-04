package com.kitakkun.jetwhale.host.data.server

import io.ktor.server.application.Application
import io.ktor.server.websocket.DefaultWebSocketServerSession

interface ServerSessionNegotiator {
    context(application: Application)
    suspend fun DefaultWebSocketServerSession.negotiate(): ServerSessionNegotiationResult
}
