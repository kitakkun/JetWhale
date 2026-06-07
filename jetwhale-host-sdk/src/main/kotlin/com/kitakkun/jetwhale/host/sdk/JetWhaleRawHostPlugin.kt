package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable

/**
 * Base class for JetWhale host plugins that handle raw string events from the debuggee.
 *
 * **New plugins should extend [JetWhaleHostPlugin] instead**, which provides typed event/method
 * handling and integrates with [JetWhaleMessagingCapablePlugin] and [JetWhaleRenderablePlugin].
 *
 * Direct use of this class is retained for backwards compatibility only.
 */
public abstract class JetWhaleRawHostPlugin {
    /**
     * Called when a raw event string is received from the debuggee.
     *
     * @deprecated Override [JetWhaleHostPlugin.onEvent] and implement
     *   [JetWhaleMessagingCapablePlugin] instead.
     */
    @Deprecated("Use JetWhaleMessagingCapablePlugin.onConnect and connection.receive instead.")
    public abstract fun onRawEvent(event: String)

    /**
     * Called when the plugin instance is disposed.
     */
    public open fun onDispose() {}

    /**
     * Composable UI for this plugin with a raw-string dispatch context.
     *
     * This is an internal bridge used by the host application.
     * Do not call this function directly.
     *
     * @deprecated Implement [JetWhaleRenderablePlugin] and override [JetWhaleRenderablePlugin.Content] instead.
     */
    @Deprecated("Implement JetWhaleRenderablePlugin and override Content() instead.")
    @Composable
    public abstract fun ContentRaw(context: JetWhaleRawDebugOperationContext)
}
