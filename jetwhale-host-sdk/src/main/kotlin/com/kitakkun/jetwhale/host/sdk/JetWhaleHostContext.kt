package com.kitakkun.jetwhale.host.sdk

/**
 * Capabilities the host exposes to a plugin instance, delivered once via
 * [JetWhaleHostPlugin.onAttach]. This is the single entry point for host-provided services so
 * that new capabilities can be added here without changing the plugin factory signature.
 */
public interface JetWhaleHostContext {
    /**
     * Persistent storage scoped to this plugin's `pluginId`. Plugins cannot reach another plugin's
     * data through it.
     */
    public val storage: JetWhalePluginStorage
}
