package com.kitakkun.jetwhale.agent.sdk

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.agent.JetWhaleAgentPluginProtocol

/**
 * Standard bidirectional event-based plugin which runs on the debug-target app.
 *
 * Supports fire-and-forget communication in both directions:
 * - [enqueueEvent]: send a [DebuggeeEvent] from the agent to the debugger
 * - [onReceiveDebuggerEvent]: receive a [DebuggerEvent] from the debugger
 *
 * For request-response communication via Method/MethodResult, use [JetWhaleMethodAgentPlugin] instead.
 */
@OptIn(InternalJetWhaleApi::class)
public abstract class JetWhaleAgentPlugin<DebuggeeEvent, DebuggerEvent> : JetWhaleRawAgentPlugin() {
    /**
     * The protocol used for encoding and decoding messages.
     */
    protected abstract val protocol: JetWhaleAgentPluginProtocol<DebuggeeEvent, DebuggerEvent>

    /**
     * Handles a typed event received from the debugger.
     * @param event The decoded debugger event.
     */
    public abstract suspend fun onReceiveDebuggerEvent(event: DebuggerEvent)

    /**
     * Called when an event is enqueued.
     * Mainly for logging or debugging purposes.
     * @param event The event message that was enqueued.
     */
    public open fun onEnqueueEvent(event: DebuggeeEvent) {}

    /**
     * Enqueues a typed event to be sent to the debugger.
     * @param event The event to be sent.
     */
    public fun enqueueEvent(event: DebuggeeEvent) {
        val rawPayload = protocol.encodeDebuggeeEvent(event)
        enqueueRawEvent(rawPayload)
        onEnqueueEvent(event)
    }

    /**
     * Handles a raw fire-and-forget event from the debugger.
     * Decodes and dispatches to [onReceiveDebuggerEvent].
     */
    final override suspend fun onRawDebuggerEvent(message: String) {
        val event = protocol.decodeDebuggerEvent(message)
        onReceiveDebuggerEvent(event)
    }

    /**
     * Not used by this plugin type. Method-based request-response is handled by [JetWhaleMethodAgentPlugin].
     */
    final override suspend fun onRawMethod(message: String): String? = null
}
