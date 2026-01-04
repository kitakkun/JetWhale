package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleProtocolVersion
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.logging.Logger
import java.util.UUID

@Inject
@ContributesBinding(AppScope::class)
class DefaultServerSessionNegotiator : ServerSessionNegotiator {
    context(application: Application)
    override suspend fun DefaultWebSocketServerSession.negotiate(): ServerSessionNegotiationResult {
        try {
            negotiateProtocolVersion(application.log)
            val (sessionId, sessionName) = negotiateSessionId()

            // TODO: Capabilities negotiation
            // TODO: Available plugins negotiation

            application.log.info("web socket accepted: $sessionId")

            return ServerSessionNegotiationResult.Success(
                sessionId = sessionId,
                sessionName = sessionName
            )
        } catch (e: Throwable) {
            application.log.error("web socket negotiation failed", e)
            return ServerSessionNegotiationResult.Failure
        }
    }

    private suspend fun DefaultWebSocketServerSession.negotiateProtocolVersion(logger: Logger) {
        val protocolVersionNegotiationRequest = receiveDeserialized<JetWhaleAgentNegotiationRequest.ProtocolVersion>()
        logger.info("Received protocol version negotiation request: $protocolVersionNegotiationRequest")
        // TODO: support multiple protocol versions
        val hostVersion = JetWhaleProtocolVersion.Current
        if (protocolVersionNegotiationRequest.version != hostVersion) {
            logger.info("Unsupported protocol version: ${protocolVersionNegotiationRequest.version}")
            sendSerialized(
                JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Reject(
                    reason = "Unsupported protocol version: ${protocolVersionNegotiationRequest.version}",
                    supportedVersions = listOf(hostVersion),
                )
            )
            throw IllegalStateException("Protocol version negotiation failed")
        } else {
            sendSerialized(JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Accept(hostVersion))
        }
    }

    // FIXME: Consider modeling return type to include sessionName
    //  instead of relying on Pair<String, String>
    private suspend fun DefaultWebSocketServerSession.negotiateSessionId(): Pair<String, String> {
        val sessionNegotiationRequest = receiveDeserialized<JetWhaleAgentNegotiationRequest.Session>()
        val requestedSessionId = sessionNegotiationRequest.sessionId
        val sessionId = requestedSessionId ?: UUID.randomUUID().toString()
        sendSerialized(JetWhaleHostNegotiationResponse.AcceptSession(sessionId))
        return sessionId to sessionNegotiationRequest.sessionName
    }
}
