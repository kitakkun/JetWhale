package com.kitakkun.jetwhale.debugger.host.sdk

import androidx.compose.runtime.Composable

/**
 * Plugin interface for JetWhale.
 *
 * Do not directly implement this interface. Use [buildJetWhaleHostPlugin] to create an instance.
 */
public interface JetWhaleHostPlugin {
    /**
     * Composable function that represents the UI of the plugin
     * @param context The context for building the UI
     */
    @Composable
    @InternalJetWhaleHostApi
    public fun Content(context: JetWhaleContentUIBuilderContext)

    /**
     * Called when an event is received from the JetWhale debugger
     * @param context The context containing the event data
     */
    @InternalJetWhaleHostApi
    public suspend fun onReceive(context: JetWhaleEventReceiverContext)

    /**
     * Called when the plugin is disposed
     */
    public fun onDispose() {
    }
}
