package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleProtocolVersion
import dev.zacsweers.metro.Inject
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.logging.Logger

@Inject
class ProtocolNegotiationStrategy : NegotiationStrategy<ProtocolVersionNegotiationResult> {
    context(logger: Logger)
    override suspend fun DefaultWebSocketServerSession.negotiate(): ProtocolVersionNegotiationResult {
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
            return ProtocolVersionNegotiationResult.Failure
        } else {
            sendSerialized(JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Accept(hostVersion))
            return ProtocolVersionNegotiationResult.Success(hostVersion)
        }
    }
}
