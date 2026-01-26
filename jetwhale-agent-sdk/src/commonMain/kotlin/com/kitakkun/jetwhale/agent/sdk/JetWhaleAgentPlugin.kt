package com.kitakkun.jetwhale.agent.sdk

import com.kitakkun.jetwhale.protocol.agent.JetWhaleAgentPluginProtocol

/**
 * Plugin client which runs on the debug-target app.
 */
public abstract class JetWhaleAgentPlugin<Event, Method, MethodResult> : JetWhaleRawAgentPlugin() {
    /**
     * The protocol used for encoding and decoding messages.
     */
    protected abstract val protocol: JetWhaleAgentPluginProtocol<Event, Method, MethodResult>

    /**
     * Handles a typed method message received from the debugger.
     * @param method The decoded method message.
     * @return The result of the method, or null if no response is needed.
     */
    public abstract suspend fun onReceiveMethod(method: Method): MethodResult?

    /**
     * Called when an event is enqueued.
     * Mainly for logging or debugging purposes.
     * @param event The event message that was enqueued.
     */
    public open fun onEnqueueEvent(event: Event) {}

    /**
     * Enqueues a typed event message to be sent to the debugger.
     * @param event The event message to be sent.
     */
    public fun enqueueEvent(event: Event) {
        val rawPayload = protocol.encodeEvent(event)
        enqueueEvent(rawPayload)
        onEnqueueEvent(event)
    }

    /**
     * Handles a raw method message received from the debugger.
     * Decodes the message, processes it, and encodes the result.
     */
    final override suspend fun onRawMethod(message: String): String? {
        val method = protocol.decodeMethod(message)
        val result = onReceiveMethod(method) ?: return null
        return protocol.encodeMethodResult(result)
    }
}
