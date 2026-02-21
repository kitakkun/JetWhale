package com.kitakkun.jetwhale.protocol.negotiation

import com.kitakkun.jetwhale.protocol.JetWhaleSerialNames
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Negotiation response sent from debugger (host) to debuggee (agent).
 * currently we have three types of responses:
 * - [ProtocolVersionResponse]: response to protocol version negotiation
 * - [AcceptSession]: response to session negotiation
 * - [AvailablePluginsResponse]: response to available plugins information
 *
 * This is a response to [JetWhaleAgentNegotiationRequest].
 */
@SerialName(JetWhaleSerialNames.NEGOTIATION_HOST)
@Serializable
public sealed interface JetWhaleHostNegotiationResponse {
    /**
     * Response to protocol version negotiation.
     * This response must be sent first when establishing connection.
     *
     * @see [JetWhaleAgentNegotiationRequest.ProtocolVersion] for request
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_HOST_PROTOCOL_VERSION_RESPONSE)
    @Serializable
    public sealed interface ProtocolVersionResponse : JetWhaleHostNegotiationResponse {
        @SerialName(JetWhaleSerialNames.NEGOTIATION_HOST_PROTOCOL_VERSION_RESPONSE_ACCEPT)
        @Serializable
        public data class Accept(val version: JetWhaleProtocolVersion) : ProtocolVersionResponse

        @SerialName(JetWhaleSerialNames.NEGOTIATION_HOST_PROTOCOL_VERSION_RESPONSE_REJECT)
        @Serializable
        public data class Reject(
            val reason: String,
            val supportedVersions: List<JetWhaleProtocolVersion>,
        ) : ProtocolVersionResponse
    }

    /**
     * Response to session negotiation.
     * This response is sent after protocol version is accepted.
     *
     * @param sessionId the accepted session ID. This session ID should be remembered by the agent to resume the session.
     * @see [JetWhaleAgentNegotiationRequest.Session] for request
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_HOST_ACCEPT_SESSION)
    @Serializable
    public data class AcceptSession(val sessionId: String) : JetWhaleHostNegotiationResponse

    /**
     * Response to capabilities information.
     * This response is sent after session is accepted.
     *
     * @param capabilities the map of capability names and their values.
     * @see [JetWhaleAgentNegotiationRequest.Capabilities] for request
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_HOST_CAPABILITIES_RESPONSE)
    @Serializable
    public data class CapabilitiesResponse(
        val capabilities: Map<String, String>,
    ) : JetWhaleHostNegotiationResponse

    /**
     * Response to available plugins information.
     * This response is sent after capabilities are exchanged.
     *
     * @param availablePlugins the list of available plugins in the host.
     * @param incompatiblePlugins the list of plugins that are incompatible with the host.
     * @see [JetWhaleAgentNegotiationRequest.AvailablePlugins] for request
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_HOST_AVAILABLE_PLUGINS_RESPONSE)
    @Serializable
    public data class AvailablePluginsResponse(
        val availablePlugins: List<JetWhalePluginInfo>,
        val incompatiblePlugins: List<JetWhalePluginInfo>,
    ) : JetWhaleHostNegotiationResponse
}
