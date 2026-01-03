package com.kitakkun.jetwhale.protocol.negotiation

import kotlinx.serialization.Serializable

/**
 * Negotiation request sent from debuggee (agent) to debugger (host).
 * currently we have three types of requests:
 * - [ProtocolVersion]: to negotiate protocol version
 * - [Session]: to request session acceptance
 * - [AvailablePlugins]: to inform available plugins
 *
 * @see [JetWhaleHostNegotiationResponse] for corresponding responses.
 */
@Serializable
sealed interface JetWhaleAgentNegotiationRequest {
    /**
     * Protocol version negotiation request.
     * This request must be sent first when establishing connection.
     *
     * @param version the protocol version of the agent.
     * @see [JetWhaleHostNegotiationResponse.ProtocolVersionResponse] for response
     */
    @Serializable
    data class ProtocolVersion(val version: JetWhaleProtocolVersion) : JetWhaleAgentNegotiationRequest

    /**
     * Session negotiation request.
     * This request is sent after protocol version is accepted.
     *
     * @param sessionId the session ID to join. If null, a new session is requested.
     * @param sessionName the name of the session which is displayed in the host UI.
     * @see [JetWhaleHostNegotiationResponse.AcceptSession] for response
     */
    @Serializable
    data class Session(
        val sessionId: String?,
        val sessionName: String,
    ) : JetWhaleAgentNegotiationRequest

    /**
     * Available plugins information request.
     * This request is sent after session is accepted.
     *
     * @param plugins the list of available plugins in the agent.
     * @see [JetWhaleHostNegotiationResponse.AvailablePluginsResponse] for response
     */
    @Serializable
    data class AvailablePlugins(val plugins: List<JetWhalePluginInfo>) : JetWhaleAgentNegotiationRequest
}
