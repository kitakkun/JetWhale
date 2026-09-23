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
    public class PluginFrameMessage(
        public val frame: PluginFrame,
    ) : JetWhaleDebuggerEvent {
        override fun equals(other: Any?): Boolean = other is PluginFrameMessage && frame == other.frame

        override fun hashCode(): Int = frame.hashCode()

        override fun toString(): String = "PluginFrameMessage(frame=$frame)"
    }

    /**
     * Notification sent from debugger when a plugin becomes available.
     *
     * @property pluginId The unique identifier of the activated plugin.
     */
    @SerialName(JetWhaleSerialNames.EVENT_HOST_PLUGIN_ACTIVATED)
    @Serializable
    public class PluginActivated(
        public val pluginId: String,
    ) : JetWhaleDebuggerEvent {
        override fun equals(other: Any?): Boolean = other is PluginActivated && pluginId == other.pluginId

        override fun hashCode(): Int = pluginId.hashCode()

        override fun toString(): String = "PluginActivated(pluginId=$pluginId)"
    }

    /**
     * Notification sent from debugger when a plugin is no longer available.
     *
     * @property pluginId The unique identifier of the deactivated plugin.
     */
    @SerialName(JetWhaleSerialNames.EVENT_HOST_PLUGIN_DEACTIVATED)
    @Serializable
    public class PluginDeactivated(
        public val pluginId: String,
    ) : JetWhaleDebuggerEvent {
        override fun equals(other: Any?): Boolean = other is PluginDeactivated && pluginId == other.pluginId

        override fun hashCode(): Int = pluginId.hashCode()

        override fun toString(): String = "PluginDeactivated(pluginId=$pluginId)"
    }
}
