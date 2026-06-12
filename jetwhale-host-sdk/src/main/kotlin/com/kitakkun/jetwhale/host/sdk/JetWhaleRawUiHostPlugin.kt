package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable

/**
 * Optional UI capability for a [JetWhaleRawHostPlugin]: a plugin that renders a UI implements this in
 * addition to extending [JetWhaleRawHostPlugin]. Plugins that don't implement it are headless and the
 * host renders no scene for them.
 *
 * Most plugins use the typed [JetWhaleUiHostPlugin] (which implements this for you) rather than this
 * raw interface directly.
 */
public interface JetWhaleRawUiHostPlugin {
    /**
     * Composable content for the plugin UI with raw String operations context.
     * This function is internally used by the JetWhale Host Application.
     * Do not call this function directly.
     *
     * @param context The context for dispatching debug operations
     */
    @Composable
    public fun ContentRaw(context: JetWhaleRawDebugOperationContext)
}
