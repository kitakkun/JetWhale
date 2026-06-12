package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable

/**
 * Optional UI capability for a [JetWhaleHostPlugin]: a plugin that renders a UI implements this in
 * addition to extending [JetWhaleHostPlugin]. Plugins that don't implement it are headless and the
 * host renders no scene for them.
 *
 * [Content] uses the plugin's [JetWhaleHostPlugin.messenger] to talk to the agent; there is no
 * separate context parameter.
 */
public interface JetWhaleHostPluginUi {
    /**
     * Composable content for the plugin UI. Composition is kept for the lifetime of the plugin
     * instance (it survives tab switches). Called internally by the host; do not call it directly.
     */
    @Composable
    public fun Content()
}
