package com.kitakkun.jetwhale.protocol.core

import com.kitakkun.jetwhale.protocol.JetWhaleSerialNames
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Events sent from debuggee (agent) to debugger (host) during a debugging session.
 */
@SerialName(JetWhaleSerialNames.EVENT_AGENT)
@Serializable
public sealed interface JetWhaleDebuggeeEvent {
    /**
     * A plugin messaging [PluginFrame] (notification, request, or reply) sent from a plugin in the
     * debuggee to its host counterpart. Plugin messaging is symmetric, so the same frame type flows
     * in both directions; this case is just its agent -> host envelope.
     */
    @SerialName(JetWhaleSerialNames.EVENT_AGENT_PLUGIN_FRAME)
    @Serializable
    public data class PluginFrameMessage(
        val frame: PluginFrame,
    ) : JetWhaleDebuggeeEvent
}
