package com.kitakkun.jetwhale.protocol.core

import com.kitakkun.jetwhale.protocol.JetWhaleSerialNames
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Events sent from debuggee (agent) to debugger (host) during debugging session.
 */
@SerialName(JetWhaleSerialNames.EVENT_AGENT)
@Serializable
public sealed interface JetWhaleDebuggeeEvent {
    /**
     * Message sent from a plugin in the debuggee to the debugger.
     * Note that this event is unidirectional and does not expect a response.
     *
     * @param pluginId The unique identifier of the plugin sending the message.
     * @param payload The content of the message.
     */
    @SerialName(JetWhaleSerialNames.EVENT_AGENT_PLUGIN_MESSAGE)
    @Serializable
    public data class PluginMessage(
        val pluginId: String,
        val payload: String,
    ) : JetWhaleDebuggeeEvent

    /**
     * Response to a method request sent by the debugger to the debuggee.
     * This event indicates the result of the requested method execution.
     *
     * @param requestId The unique identifier of the original method request.
     */
    @SerialName(JetWhaleSerialNames.EVENT_AGENT_METHOD_RESULT_RESPONSE)
    @Serializable
    public sealed interface MethodResultResponse : JetWhaleDebuggeeEvent {
        public val requestId: String

        /**
         * Indicates that the method request has failed.
         * @param errorMessage A message describing the reason for the failure.
         */
        @SerialName(JetWhaleSerialNames.EVENT_AGENT_METHOD_RESULT_RESPONSE_FAILED)
        @Serializable
        public data class Failed(
            override val requestId: String,
            val errorMessage: String,
        ) : MethodResultResponse

        /**
         * Indicates that the method request has succeeded.
         * @param payload The result of the method execution.
         */
        @SerialName(JetWhaleSerialNames.EVENT_AGENT_METHOD_RESULT_RESPONSE_SUCCESS)
        @Serializable
        public data class Success(
            override val requestId: String,
            val payload: String?,
        ) : MethodResultResponse
    }
}
