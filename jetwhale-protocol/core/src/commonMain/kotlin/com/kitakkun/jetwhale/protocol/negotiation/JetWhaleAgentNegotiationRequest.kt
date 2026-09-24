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
     * @property version the protocol version of the agent.
     * @see [JetWhaleHostNegotiationResponse.ProtocolVersionResponse] for response
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_AGENT_PROTOCOL_VERSION)
    @Serializable
    public class ProtocolVersion(public val version: JetWhaleProtocolVersion) : JetWhaleAgentNegotiationRequest {
        override fun equals(other: Any?): Boolean = other is ProtocolVersion && version == other.version

        override fun hashCode(): Int = version.hashCode()

        override fun toString(): String = "ProtocolVersion(version=$version)"
    }

    /**
     * Session negotiation request.
     * This request is sent after protocol version is accepted.
     *
     * @property sessionId the session ID to join. If null, a new session is requested.
     * @property sessionName the name of the session which is displayed in the host UI.
     * @property appMetadata optional application/device metadata used by the host to group sessions.
     *   Added additively with a default so older hosts and agents keep interoperating.
     * @see [JetWhaleHostNegotiationResponse.AcceptSession] for response
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_AGENT_SESSION)
    @Serializable
    public class Session(
        public val sessionId: String?,
        public val sessionName: String,
        public val appMetadata: JetWhaleAppMetadata = JetWhaleAppMetadata(),
    ) : JetWhaleAgentNegotiationRequest {
        override fun equals(other: Any?): Boolean = other is Session &&
            sessionId == other.sessionId &&
            sessionName == other.sessionName &&
            appMetadata == other.appMetadata

        override fun hashCode(): Int {
            var result = sessionId.hashCode()
            result = 31 * result + sessionName.hashCode()
            result = 31 * result + appMetadata.hashCode()
            return result
        }

        override fun toString(): String = "Session(sessionId=$sessionId, sessionName=$sessionName, appMetadata=$appMetadata)"
    }

    /**
     * Capabilities information request.
     * This request is sent after session is accepted.
     *
     * @property capabilities the map of capability names and their values.
     * @see [JetWhaleHostNegotiationResponse.CapabilitiesResponse] for response
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_AGENT_CAPABILITIES)
    @Serializable
    public class Capabilities(
        public val capabilities: Map<String, String>,
    ) : JetWhaleAgentNegotiationRequest {
        override fun equals(other: Any?): Boolean = other is Capabilities && capabilities == other.capabilities

        override fun hashCode(): Int = capabilities.hashCode()

        override fun toString(): String = "Capabilities(capabilities=$capabilities)"
    }

    /**
     * Available plugins information request.
     * This request is sent after capabilities are exchanged.
     *
     * @property plugins the list of available plugins in the agent.
     * @see [JetWhaleHostNegotiationResponse.AvailablePluginsResponse] for response
     */
    @SerialName(JetWhaleSerialNames.NEGOTIATION_AGENT_AVAILABLE_PLUGINS)
    @Serializable
    public class AvailablePlugins(public val plugins: List<JetWhalePluginInfo>) : JetWhaleAgentNegotiationRequest {
        override fun equals(other: Any?): Boolean = other is AvailablePlugins && plugins == other.plugins

        override fun hashCode(): Int = plugins.hashCode()

        override fun toString(): String = "AvailablePlugins(plugins=$plugins)"
    }
}
