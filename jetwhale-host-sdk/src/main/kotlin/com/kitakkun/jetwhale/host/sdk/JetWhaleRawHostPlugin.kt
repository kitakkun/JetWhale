package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable

/**
 * Base class for JetWhale Host Plugins that handle raw events from the debuggee.
 */
public abstract class JetWhaleRawHostPlugin {
    /**
     * Called when an event is received from debuggee
     * @param event The received raw event as a String
     */
    public abstract fun onRawEvent(event: String)

    /**
     * Called when the plugin instance is disposed
     */
    public open fun onDispose() {}

    /**
     * Composable content for the plugin UI with raw String operations context
     * This function is internally used by the JetWhale Host Application.
     * Do not call this function directly.
     *
     * @param context The context for dispatching debug operations
     */
    @Composable
    public abstract fun ContentRaw(context: JetWhaleDebugOperationContext<String, String>)
}
