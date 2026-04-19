package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.protocol.host.JetWhaleHostPluginProtocol
import kotlinx.coroutines.CoroutineScope

/**
 * Plugin interface for JetWhale.
 */
public abstract class JetWhaleHostPlugin<Event, Method, MethodResult> : JetWhaleRawHostPlugin() {
    /**
     * The protocol used for encoding and decoding events, methods, and method results
     */
    protected abstract val protocol: JetWhaleHostPluginProtocol<Event, Method, MethodResult>

    /**
     * Called when an event is received from debuggee
     * @param event The received event
     */
    public abstract fun onEvent(event: Event)

    /**
     * Composable function that represents the UI of the plugin
     *
     * Composition will be kept as long as the plugin instance is alive.
     * Even if the tab is switched, the composition will be kept.
     *
     * @param context The context to perform debug operations
     */
    @Composable
    public abstract fun Content(context: JetWhaleDebugOperationContext<Method, MethodResult>)

    /**
     * Handles a raw event message received from the debuggee.
     * Decodes the message and processes it.
     */
    final override fun onRawEvent(event: String) {
        val decodedEvent = protocol.decodeEvent(event)
        onEvent(decodedEvent)
    }

    /**
     * Composable function that adapts raw string-based context to typed context
     * and calls the typed Content function.
     * @param context The raw debug operation context
     */
    @Composable
    final override fun ContentRaw(context: JetWhaleRawDebugOperationContext) {
        val typedContext = object : JetWhaleDebugOperationContext<Method, MethodResult> {
            override val coroutineScope: CoroutineScope = context.coroutineScope
            override suspend fun <MR : MethodResult> dispatch(method: Method): MR? {
                val encodedMethod = protocol.encodeMethod(method)
                val rawResult = context.dispatch(encodedMethod)
                @Suppress("UNCHECKED_CAST")
                return rawResult?.let { protocol.decodeMethodResult(it) } as? MR
            }
        }
        Content(context = typedContext)
    }
}
