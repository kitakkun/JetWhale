package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAgentNegotiationRequest
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleHostNegotiationResponse
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleProtocolVersion
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.logging.Logger
import java.util.UUID

@Inject
@ContributesBinding(AppScope::class)
class DefaultServerSessionNegotiationStrategy : ServerSessionNegotiationStrategy {
    context(logger: Logger)
    override suspend fun DefaultWebSocketServerSession.negotiate(): ServerSessionNegotiationResult {
        val protocol = negotiateProtocolVersion()

        if (protocol is ProtocolVersionNegotiationResult.Failure) {
            return ServerSessionNegotiationResult.Failure
        }

        val session = negotiateSessionId()

        negotiateCapabilities()

        val plugin = negotiatePlugins()

        return ServerSessionNegotiationResult.Success(
            session = session,
            plugin = plugin,
        )
    }

    context(logger: Logger)
    private suspend fun DefaultWebSocketServerSession.negotiateProtocolVersion(): ProtocolVersionNegotiationResult {
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

    private suspend fun DefaultWebSocketServerSession.negotiateSessionId(): SessionNegotiationResult {
        val sessionNegotiationRequest = receiveDeserialized<JetWhaleAgentNegotiationRequest.Session>()
        val requestedSessionId = sessionNegotiationRequest.sessionId
        val sessionId = requestedSessionId ?: UUID.randomUUID().toString()
        sendSerialized(JetWhaleHostNegotiationResponse.AcceptSession(sessionId))
        return SessionNegotiationResult(
            sessionId = sessionId,
            sessionName = sessionNegotiationRequest.sessionName,
        )
    }

    private suspend fun DefaultWebSocketServerSession.negotiateCapabilities(): JetWhaleHostNegotiationResponse.CapabilitiesResponse {
        receiveDeserialized<JetWhaleAgentNegotiationRequest.Capabilities>()
        val response = JetWhaleHostNegotiationResponse.CapabilitiesResponse(
            capabilities = mapOf(), // TODO: Provide actual capabilities
        )
        sendSerialized(response)
        return response
    }

    private suspend fun DefaultWebSocketServerSession.negotiatePlugins(): PluginNegotiationResult {
        val request = receiveDeserialized<JetWhaleAgentNegotiationRequest.AvailablePlugins>()
        sendSerialized(
            JetWhaleHostNegotiationResponse.AvailablePluginsResponse(
                availablePlugins = listOf(), // TODO: Provide actual available plugins
                incompatiblePlugins = listOf(), // TODO: Provide actual incompatible plugins
            )
        )
        return PluginNegotiationResult(requestedPlugins = request.plugins)
    }
}
