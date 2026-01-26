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

        val requestedVersion = protocolVersionNegotiationRequest.version

        if (requestedVersion !in SUPPORTED_VERSIONS) {
            logger.info("Unsupported protocol version: ${requestedVersion.version}")
            sendSerialized(
                JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Reject(
                    reason = "Unsupported protocol version: ${requestedVersion.version}",
                    supportedVersions = SUPPORTED_VERSIONS,
                )
            )
            return ProtocolVersionNegotiationResult.Failure
        }

        logger.info("Accepted protocol version: ${requestedVersion.version}")

        sendSerialized(JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Accept(requestedVersion))
        return ProtocolVersionNegotiationResult.Success(requestedVersion)
    }

    companion object {
        private val SUPPORTED_VERSION_INT_RANGE = 2..JetWhaleProtocolVersion.Current.version
        private val SUPPORTED_VERSIONS = SUPPORTED_VERSION_INT_RANGE.map { JetWhaleProtocolVersion(it) }
    }
}
