package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession

/**
 * A no-op negotiation strategy for testing purposes.
 * This strategy skips all negotiation steps and immediately returns a successful result.
 */
internal class NoopClientSessionNegotiationStrategy : ClientSessionNegotiationStrategy {
    override suspend fun DefaultClientWebSocketSession.negotiate(): ClientSessionNegotiationResult {
        return ClientSessionNegotiationResult.Success(availablePluginIds = emptyList())
    }
}