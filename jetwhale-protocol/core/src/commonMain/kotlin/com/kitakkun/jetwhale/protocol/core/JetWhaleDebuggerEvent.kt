package com.kitakkun.jetwhale.protocol.core

import com.kitakkun.jetwhale.protocol.JetWhaleSerialNames
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Events sent from debugger (host) to debuggee (agent).
 */
@SerialName(JetWhaleSerialNames.EVENT_HOST)
@Serializable
public sealed interface JetWhaleDebuggerEvent {
    /**
     * Method request sent from debugger to debuggee.
     * This event expects a response from the debuggee.
     *
     * @param pluginId The unique identifier of the target plugin.
     * @param requestId The unique identifier for this request.
     * @param payload The content of the method request.
     */
    @SerialName(JetWhaleSerialNames.EVENT_HOST_PLUGIN_METHOD_REQUEST)
    @Serializable
    public data class MethodRequest(
        val pluginId: String,
        val requestId: String,
        val payload: String,
    ) : JetWhaleDebuggerEvent

    /**
     * Notification sent from debugger when a plugin becomes available.
     *
     * @param pluginId The unique identifier of the activated plugin.
     * @param pluginVersion The version of the activated plugin.
     */
    @SerialName(JetWhaleSerialNames.EVENT_HOST_PLUGIN_ACTIVATED)
    @Serializable
    public data class PluginActivated(
        val pluginId: String,
        val pluginVersion: String,
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
