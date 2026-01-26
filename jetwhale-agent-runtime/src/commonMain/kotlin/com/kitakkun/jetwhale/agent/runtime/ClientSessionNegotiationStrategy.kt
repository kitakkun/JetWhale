package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession

/**
 * An interface for negotiating a WebSocket session.
 * The negotiation is required before starting a debugging session.
 *
 * Implementations should handle the negotiation process according to the JetWhale protocol.
 */
internal interface ClientSessionNegotiationStrategy {
    /**
     * Negotiates the WebSocket session.
     * @return The result of the session negotiation.
     */
    suspend fun DefaultClientWebSocketSession.negotiate(): ClientSessionNegotiationResult
}
