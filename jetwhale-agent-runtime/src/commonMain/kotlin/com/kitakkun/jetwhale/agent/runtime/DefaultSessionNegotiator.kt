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
        // protocol version negotiation
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

        // sessionId negotiation
        JetWhaleLogger.v("Starting session negotiation")
        if (sessionId == null) {
            JetWhaleLogger.v("Requesting new session")
        } else {
            JetWhaleLogger.d("Resuming existing session with sessionId: $sessionId")
        }
        sendSerialized(
            JetWhaleAgentNegotiationRequest.Session(
                sessionId = sessionId,
                sessionName = getDeviceModelName(),
            )
        )
        JetWhaleLogger.v("Sent session negotiation request" + " with sessionId: $sessionId".takeIf { sessionId != null })

        sessionId = receiveDeserialized<JetWhaleHostNegotiationResponse.AcceptSession>().sessionId
        JetWhaleLogger.d("Session negotiation completed with sessionId: $sessionId")

        // TODO: Capabilities negotiation (currently not implemented)

        // TODO: Available plugins negotiation (currently not implemented)
    }
}
