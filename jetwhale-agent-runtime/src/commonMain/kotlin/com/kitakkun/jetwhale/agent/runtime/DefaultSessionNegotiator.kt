package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleProtocolVersion
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized

internal class DefaultSessionNegotiator : SessionNegotiator {
    private var sessionId: String? = null

    override suspend fun DefaultClientWebSocketSession.negotiate() {
        negotiateProtocolVersion()
        sessionId = negotiateSessionId(sessionId)

        // TODO: Capabilities negotiation (currently not implemented)

        // TODO: Available plugins negotiation (currently not implemented)
    }

    private suspend fun DefaultClientWebSocketSession.negotiateProtocolVersion() {
        JetWhaleLogger.v("Starting protocol version negotiation")
        sendSerialized(JetWhaleAgentNegotiationRequest.ProtocolVersion(JetWhaleProtocolVersion.Current))
        val protocolNegotiationResponse: JetWhaleHostNegotiationResponse.ProtocolVersionResponse = receiveDeserialized()
        JetWhaleLogger.v("Received protocol version negotiation response: $protocolNegotiationResponse")

        when (protocolNegotiationResponse) {
            is JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Accept -> {
                JetWhaleLogger.d("Protocol version accepted: ${protocolNegotiationResponse.version}")
            }

            is JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Reject -> {
                JetWhaleLogger.e("Incompatible protocol version. Rejected by host: ${protocolNegotiationResponse.reason}")
                throw IllegalStateException("Protocol version rejected by host: ${protocolNegotiationResponse.reason}")
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.negotiateSessionId(resumingSessionId: String?): String {
        JetWhaleLogger.v("Starting session negotiation")
        if (resumingSessionId == null) {
            JetWhaleLogger.v("Requesting new session")
        } else {
            JetWhaleLogger.d("Resuming existing session with sessionId: $resumingSessionId")
        }
        sendSerialized(
            JetWhaleAgentNegotiationRequest.Session(
                sessionId = resumingSessionId,
                sessionName = getDeviceModelName(),
            )
        )
        JetWhaleLogger.v("Sent session negotiation request" + " with sessionId: $resumingSessionId".takeIf { resumingSessionId != null })

        val assignedSessionId = receiveDeserialized<JetWhaleHostNegotiationResponse.AcceptSession>().sessionId
        JetWhaleLogger.d("Session negotiation completed with sessionId: $assignedSessionId")

        return assignedSessionId
    }
}
