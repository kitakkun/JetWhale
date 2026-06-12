package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope

/**
 * Base class for typed JetWhale host plugins that render a UI. It is a [JetWhaleHostPlugin] that also
 * provides a Compose [Content], adapting the host's raw [JetWhaleRawDebugOperationContext] to the
 * typed [JetWhaleDebugOperationContext] for you.
 *
 * Extend this instead of [JetWhaleHostPlugin] when the plugin has a UI; extend [JetWhaleHostPlugin]
 * directly for a headless plugin.
 */
public abstract class JetWhaleUiHostPlugin<Event, Method, MethodResult> :
    JetWhaleHostPlugin<Event, Method, MethodResult>(),
    JetWhaleRawUiHostPlugin {
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
