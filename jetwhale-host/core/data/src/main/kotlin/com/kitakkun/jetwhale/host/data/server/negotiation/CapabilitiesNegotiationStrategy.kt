package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import dev.zacsweers.metro.Inject
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.logging.Logger

@Inject
class CapabilitiesNegotiationStrategy : NegotiationStrategy<JetWhaleHostNegotiationResponse.CapabilitiesResponse> {
    context(logger: Logger)
    override suspend fun DefaultWebSocketServerSession.negotiate(): JetWhaleHostNegotiationResponse.CapabilitiesResponse {
        receiveDeserialized<JetWhaleAgentNegotiationRequest.Capabilities>()
        val response = JetWhaleHostNegotiationResponse.CapabilitiesResponse(
            capabilities = mapOf(), // TODO: Provide actual capabilities
        )
        sendSerialized(response)
        return response
    }
}
