package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.protocol.host.JetWhaleHostPluginProtocol
import kotlinx.coroutines.CoroutineScope

/**
 * Standard bidirectional event-based plugin interface for JetWhale Host.
 *
 * Supports fire-and-forget communication in both directions:
 * - [onEvent]: receive a [DebuggeeEvent] from the debuggee
 * - [JetWhaleDebugOperationContext.send]: send a [DebuggerEvent] to the debuggee
 *
 * For request-response communication via Method/MethodResult, use [JetWhaleMethodHostPlugin] instead.
 */
public abstract class JetWhaleHostPlugin<DebuggeeEvent, DebuggerEvent> : JetWhaleRawHostPlugin() {
    /**
     * The protocol used for encoding and decoding events
     */
    protected abstract val protocol: JetWhaleHostPluginProtocol<DebuggeeEvent, DebuggerEvent>

    /**
     * Called when an event is received from debuggee
     * @param event The received event
     */
    public abstract fun onEvent(event: DebuggeeEvent)

    /**
     * Composable function that represents the UI of the plugin
     *
     * Composition will be kept as long as the plugin instance is alive.
     * Even if the tab is switched, the composition will be kept.
     *
     * @param context The context to send debugger events
     */
    @Composable
    public abstract fun Content(context: JetWhaleDebugOperationContext<DebuggerEvent>)

    /**
     * Handles a raw event message received from the debuggee.
     * Decodes the message and processes it.
     */
    final override fun onRawEvent(event: String) {
        val decodedEvent = protocol.decodeDebuggeeEvent(event)
        onEvent(decodedEvent)
    }

    /**
     * Composable function that adapts raw string-based context to typed context
     * and calls the typed Content function.
     * @param context The raw debug operation context
     */
    @Composable
    final override fun ContentRaw(context: JetWhaleRawDebugOperationContext) {
        val typedContext = object : JetWhaleDebugOperationContext<DebuggerEvent> {
            override val coroutineScope: CoroutineScope = context.coroutineScope
            override suspend fun send(event: DebuggerEvent) {
                context.send(protocol.encodeDebuggerEvent(event))
            }
        }
        Content(context = typedContext)
    }
}
