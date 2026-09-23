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
        public class Accept(public val version: JetWhaleProtocolVersion) : ProtocolVersionResponse {
            override fun equals(other: Any?): Boolean = other is Accept && version == other.version

            override fun hashCode(): Int = version.hashCode()

            override fun toString(): String = "Accept(version=$version)"
        }

        @SerialName(JetWhaleSerialNames.NEGOTIATION_HOST_PROTOCOL_VERSION_RESPONSE_REJECT)
        @Serializable
        public class Reject(
            public val reason: String,
            public val supportedVersions: List<JetWhaleProtocolVersion>,
        ) : ProtocolVersionResponse {
            override fun equals(other: Any?): Boolean = other is Reject && reason == other.reason && supportedVersions == other.supportedVersions

            override fun hashCode(): Int = 31 * reason.hashCode() + supportedVersions.hashCode()

            override fun toString(): String = "Reject(reason=$reason, supportedVersions=$supportedVersions)"
        }
    }

    /**
     * Response to session negotiation.
     * This response is sent after protocol version is accepted.
     *
     * @property sessionId the accepted session ID. This session ID should be remembered by the agent to resume the session.
     * @see [JetWhaleAgentNegotiationRequest.Session] for request
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_HOST_ACCEPT_SESSION)
    @Serializable
    public class AcceptSession(public val sessionId: String) : JetWhaleHostNegotiationResponse {
        override fun equals(other: Any?): Boolean = other is AcceptSession && sessionId == other.sessionId

        override fun hashCode(): Int = sessionId.hashCode()

        override fun toString(): String = "AcceptSession(sessionId=$sessionId)"
    }

    /**
     * Response to capabilities information.
     * This response is sent after session is accepted.
     *
     * @property capabilities the map of capability names and their values.
     * @see [JetWhaleAgentNegotiationRequest.Capabilities] for request
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_HOST_CAPABILITIES_RESPONSE)
    @Serializable
    public class CapabilitiesResponse(
        public val capabilities: Map<String, String>,
    ) : JetWhaleHostNegotiationResponse {
        override fun equals(other: Any?): Boolean = other is CapabilitiesResponse && capabilities == other.capabilities

        override fun hashCode(): Int = capabilities.hashCode()

        override fun toString(): String = "CapabilitiesResponse(capabilities=$capabilities)"
    }

    /**
     * Response to available plugins information.
     * This response is sent after capabilities are exchanged.
     *
     * @property availablePlugins the list of available plugins in the host.
     * @property incompatiblePlugins the list of plugins that are incompatible with the host.
     * @see [JetWhaleAgentNegotiationRequest.AvailablePlugins] for request
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_HOST_AVAILABLE_PLUGINS_RESPONSE)
    @Serializable
    public class AvailablePluginsResponse(
        public val availablePlugins: List<JetWhalePluginInfo>,
        public val incompatiblePlugins: List<JetWhalePluginInfo>,
    ) : JetWhaleHostNegotiationResponse {
        override fun equals(other: Any?): Boolean = other is AvailablePluginsResponse &&
            availablePlugins == other.availablePlugins &&
            incompatiblePlugins == other.incompatiblePlugins

        override fun hashCode(): Int = 31 * availablePlugins.hashCode() + incompatiblePlugins.hashCode()

        override fun toString(): String = "AvailablePluginsResponse(availablePlugins=$availablePlugins, " +
            "incompatiblePlugins=$incompatiblePlugins)"
    }
}
