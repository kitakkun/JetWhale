package com.kitakkun.jetwhale.host.sdk

/**
 * Base class for JetWhale Host Plugins that handle raw events from the debuggee.
 *
 * This base is **headless**: it carries no UI. A plugin that renders a UI additionally implements
 * [JetWhaleRawUiHostPlugin] (or, for the typed variant, extends [JetWhaleUiHostPlugin] instead of
 * [JetWhaleHostPlugin]).
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
}
