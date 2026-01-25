package com.kitakkun.jetwhale.host.data.server.negotiation

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.util.logging.Logger

/**
 * A strategy interface for negotiating various aspects of a connection.
 *
 * @param Result The type of the negotiation result.
 */
interface NegotiationStrategy<Result> {
    context(logger: Logger)
    suspend fun DefaultWebSocketServerSession.negotiate(): Result
}
