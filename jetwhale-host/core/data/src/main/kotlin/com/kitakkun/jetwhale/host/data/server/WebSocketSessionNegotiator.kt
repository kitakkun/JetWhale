package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleProtocolVersion
import dev.zacsweers.metro.Inject
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import java.util.UUID

@Inject
class WebSocketSessionNegotiator {
    sealed interface NegotiationResult {
        data class Success(
            val sessionId: String,
            val sessionName: String
        ) : NegotiationResult

        data object Failure : NegotiationResult
    }

    context(application: Application)
    suspend fun DefaultWebSocketServerSession.negotiate(): NegotiationResult {
        val protocolVersionNegotiationRequest = receiveDeserialized<JetWhaleAgentNegotiationRequest.ProtocolVersion>()
        application.log.info("Received protocol version negotiation request: $protocolVersionNegotiationRequest")

        // TODO: support multiple protocol versions
        val hostVersion = JetWhaleProtocolVersion.Current
        if (protocolVersionNegotiationRequest.version != hostVersion) {
            application.log.info("Unsupported protocol version: ${protocolVersionNegotiationRequest.version}")
            sendSerialized(
                JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Reject(
                    reason = "Unsupported protocol version: ${protocolVersionNegotiationRequest.version}",
                    supportedVersions = listOf(hostVersion),
                )
            )
            return NegotiationResult.Failure
        } else {
            sendSerialized(JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Accept(hostVersion))
        }

        // Session negotiation
        val sessionNegotiationRequest = receiveDeserialized<JetWhaleAgentNegotiationRequest.Session>()

        val requestedSessionId = sessionNegotiationRequest.sessionId
        val sessionId = requestedSessionId ?: UUID.randomUUID().toString()
        sendSerialized(JetWhaleHostNegotiationResponse.AcceptSession(sessionId))

        // TODO: Capabilities negotiation
        // TODO: Available plugins negotiation

        application.log.info("web socket accepted: $sessionId")

        return NegotiationResult.Success(
            sessionId = sessionId,
            sessionName = sessionNegotiationRequest.sessionName
        )
    }
}
