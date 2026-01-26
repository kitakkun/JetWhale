package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleProtocolVersion
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized

internal class DefaultClientSessionNegotiationStrategy(private val plugins: List<AgentPlugin>) : ClientSessionNegotiationStrategy {
    private var sessionId: String? = null

    override suspend fun DefaultClientWebSocketSession.negotiate(): ClientSessionNegotiationResult {
        return try {
            negotiateProtocolVersion()
            sessionId = negotiateSessionId(sessionId)

            // Currently, we are not using the capabilities response for anything,
            // Just for future extensibility.
            negotiateCapabilities()

            val response = negotiatePlugins(plugins)

            ClientSessionNegotiationResult.Success(availablePluginIds = response.availablePlugins.map { it.pluginId })
        } catch (e: Throwable) {
            ClientSessionNegotiationResult.Failure(reason = e.message ?: "Unknown error during negotiation")
        }
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

    private suspend fun DefaultClientWebSocketSession.negotiateCapabilities(): JetWhaleHostNegotiationResponse.CapabilitiesResponse {
        val request = JetWhaleAgentNegotiationRequest.Capabilities(capabilities = emptyMap())
        sendSerialized(request)
        return receiveDeserialized()
    }

    private suspend fun DefaultClientWebSocketSession.negotiatePlugins(plugins: List<AgentPlugin>): JetWhaleHostNegotiationResponse.AvailablePluginsResponse {
        val request = JetWhaleAgentNegotiationRequest.AvailablePlugins(
            plugins = plugins.map {
                JetWhalePluginInfo(
                    pluginId = it.pluginId,
                    pluginVersion = it.pluginVersion,
                )
            }
        )
        sendSerialized(request)
        return receiveDeserialized()
    }
}
