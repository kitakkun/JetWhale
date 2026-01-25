package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import dev.zacsweers.metro.Inject
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.logging.Logger
import java.util.UUID

@Inject
class SessionNegotiationStrategy : NegotiationStrategy<SessionNegotiationResult> {
    context(logger: Logger)
    override suspend fun DefaultWebSocketServerSession.negotiate(): SessionNegotiationResult {
        val sessionNegotiationRequest = receiveDeserialized<JetWhaleAgentNegotiationRequest.Session>()
        val requestedSessionId = sessionNegotiationRequest.sessionId
        val sessionId = requestedSessionId ?: UUID.randomUUID().toString()
        sendSerialized(JetWhaleHostNegotiationResponse.AcceptSession(sessionId))
        return SessionNegotiationResult(
            sessionId = sessionId,
            sessionName = sessionNegotiationRequest.sessionName,
        )
    }
}
