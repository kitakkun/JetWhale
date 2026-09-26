package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.host.model.HostSession
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
        // The host session's id names no app; an agent asking for it gets a fresh id like any other.
        val sessionId = sessionNegotiationRequest.sessionId
            ?.takeUnless(HostSession::isHost)
            ?: UUID.randomUUID().toString()
        sendSerialized(JetWhaleHostNegotiationResponse.AcceptSession(sessionId))
        return SessionNegotiationResult(
            sessionId = sessionId,
            sessionName = sessionNegotiationRequest.sessionName,
            appMetadata = sessionNegotiationRequest.appMetadata,
        )
    }
}
