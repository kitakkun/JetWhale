package com.kitakkun.jetwhale.host.sdk

public interface JetWhaleHostPluginFactory {
    /**
     * Creates an instance of the plugin.
     * @return An instance of [JetWhaleHostPlugin].
     */
    public fun createPlugin(): JetWhaleHostPlugin
}
