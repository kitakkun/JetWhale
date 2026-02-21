package com.kitakkun.jetwhale.protocol.negotiation

import com.kitakkun.jetwhale.protocol.JetWhaleSerialNames
import kotlinx.serialization.SerialName
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
@SerialName(JetWhaleSerialNames.NEGOTIATION_AGENT)
@Serializable
public sealed interface JetWhaleAgentNegotiationRequest {
    /**
     * Protocol version negotiation request.
     * This request must be sent first when establishing connection.
     *
     * @param version the protocol version of the agent.
     * @see [JetWhaleHostNegotiationResponse.ProtocolVersionResponse] for response
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_AGENT_PROTOCOL_VERSION)
    @Serializable
    public data class ProtocolVersion(val version: JetWhaleProtocolVersion) : JetWhaleAgentNegotiationRequest

    /**
     * Session negotiation request.
     * This request is sent after protocol version is accepted.
     *
     * @param sessionId the session ID to join. If null, a new session is requested.
     * @param sessionName the name of the session which is displayed in the host UI.
     * @see [JetWhaleHostNegotiationResponse.AcceptSession] for response
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_AGENT_SESSION)
    @Serializable
    public data class Session(
        val sessionId: String?,
        val sessionName: String,
    ) : JetWhaleAgentNegotiationRequest

    /**
     * Capabilities information request.
     * This request is sent after session is accepted.
     *
     * @param capabilities the map of capability names and their values.
     * @see [JetWhaleHostNegotiationResponse.CapabilitiesResponse] for response
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_AGENT_CAPABILITIES)
    @Serializable
    public data class Capabilities(
        val capabilities: Map<String, String>,
    ) : JetWhaleAgentNegotiationRequest

    /**
     * Available plugins information request.
     * This request is sent after capabilities are exchanged.
     *
     * @param plugins the list of available plugins in the agent.
     * @see [JetWhaleHostNegotiationResponse.AvailablePluginsResponse] for response
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_AGENT_AVAILABLE_PLUGINS)
    @Serializable
    public data class AvailablePlugins(val plugins: List<JetWhalePluginInfo>) : JetWhaleAgentNegotiationRequest
}
