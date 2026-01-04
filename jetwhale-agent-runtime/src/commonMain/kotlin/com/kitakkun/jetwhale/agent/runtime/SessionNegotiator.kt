package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession

/**
 * An interface for negotiating a WebSocket session.
 * The negotiation is required before starting a debugging session.
 *
 * Implementations should handle the negotiation process according to the JetWhale protocol.
 */
internal interface SessionNegotiator {
    suspend fun DefaultClientWebSocketSession.negotiate()
}
