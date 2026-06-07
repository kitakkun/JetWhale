package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable

/**
 * Optional capability interface for plugins that provide a UI panel in the JetWhale host application.
 *
 * Implement this interface alongside [JetWhaleRawHostPlugin] to render a Compose UI panel for the plugin.
 * The host application displays a tab for each active plugin instance that implements this interface.
 *
 * If the plugin also implements [JetWhaleMessagingCapablePlugin], the connection is established before
 * [Content] is first composed, so method dispatch via the stored context is safe inside [Content].
 */
public interface JetWhaleRenderablePlugin {
    /**
     * Composable content for this plugin's UI panel.
     *
     * The composition is kept alive as long as the plugin instance is active,
     * even when the user switches to a different tab.
     */
    @Composable
    public fun Content()
}
