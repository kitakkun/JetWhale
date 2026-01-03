package com.kitakkun.jetwhale.protocol.core

import kotlinx.serialization.Serializable

/**
 * Events sent from debugger (host) to debuggee (agent).
 */
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
    @Serializable
    public data class MethodRequest(
        val pluginId: String,
        val requestId: String,
        val payload: String,
    ) : JetWhaleDebuggerEvent
}
