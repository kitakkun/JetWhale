package com.kitakkun.jetwhale.host.sdk

public interface JetWhaleHostPluginFactory {
    /**
     * Creates an instance of the plugin.
     *
     * @param context The host services available to the instance (adb, sessions). Hold it if the
     *   plugin needs it later; it stays valid for the life of the instance.
     * @return An instance of [JetWhaleHostPlugin].
     */
    public fun createPlugin(context: JetWhaleHostPluginContext): JetWhaleHostPlugin
}
