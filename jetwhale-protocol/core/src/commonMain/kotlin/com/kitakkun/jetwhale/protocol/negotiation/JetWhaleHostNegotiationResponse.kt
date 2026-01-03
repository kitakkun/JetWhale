package com.kitakkun.jetwhale.protocol.negotiation

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
@Serializable
public sealed interface JetWhaleHostNegotiationResponse {
    /**
     * Response to protocol version negotiation.
     * This response must be sent first when establishing connection.
     *
     * @see [JetWhaleAgentNegotiationRequest.ProtocolVersion] for request
     */
    @Serializable
    public sealed interface ProtocolVersionResponse : JetWhaleHostNegotiationResponse {
        @Serializable
        public data class Accept(val version: JetWhaleProtocolVersion) : ProtocolVersionResponse

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
    @Serializable
    public data class AcceptSession(val sessionId: String) : JetWhaleHostNegotiationResponse

    /**
     * Response to available plugins information.
     * This response is sent after session is accepted.
     *
     * @param availablePlugins the list of available plugins in the host.
     * @param incompatiblePlugins the list of plugins that are incompatible with the host.
     * @see [JetWhaleAgentNegotiationRequest.AvailablePlugins] for request
     */
    @Serializable
    public data class AvailablePluginsResponse(
        val availablePlugins: List<JetWhalePluginInfo>,
        val incompatiblePlugins: List<JetWhalePluginInfo>,
    ) : JetWhaleHostNegotiationResponse
}
