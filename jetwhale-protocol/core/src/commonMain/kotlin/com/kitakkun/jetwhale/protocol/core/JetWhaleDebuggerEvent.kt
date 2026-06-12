package com.kitakkun.jetwhale.protocol.core

import com.kitakkun.jetwhale.protocol.JetWhaleSerialNames
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Events sent from debugger (host) to debuggee (agent).
 */
@SerialName(JetWhaleSerialNames.EVENT_HOST)
@Serializable
public sealed interface JetWhaleDebuggerEvent {
    /**
     * A plugin messaging [PluginFrame] (notification, request, or reply) addressed to a plugin in
     * the debuggee. Plugin messaging is symmetric, so the same frame type flows in both directions;
     * this case is just its host -> agent envelope.
     */
    @SerialName(JetWhaleSerialNames.EVENT_HOST_PLUGIN_FRAME)
    @Serializable
    public data class PluginFrameMessage(
        val frame: PluginFrame,
    ) : JetWhaleDebuggerEvent

    /**
     * Notification sent from debugger when a plugin becomes available.
     *
     * @param pluginId The unique identifier of the activated plugin.
     */
    @SerialName(JetWhaleSerialNames.EVENT_HOST_PLUGIN_ACTIVATED)
    @Serializable
    public data class PluginActivated(
        val pluginId: String,
    ) : JetWhaleDebuggerEvent

    /**
     * Notification sent from debugger when a plugin is no longer available.
     *
     * @param pluginId The unique identifier of the deactivated plugin.
     */
    @SerialName(JetWhaleSerialNames.EVENT_HOST_PLUGIN_DEACTIVATED)
    @Serializable
    public data class PluginDeactivated(
        val pluginId: String,
    ) : JetWhaleDebuggerEvent
}
