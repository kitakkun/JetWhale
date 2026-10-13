package com.kitakkun.jetwhale.host.sdk

public interface JetWhaleHostPluginFactory {
    /**
     * Creates an instance of the plugin.
     *
     * @param context The host's own capabilities, such as adb. It stays valid for the life of the
     *   instance, so keep what the plugin needs.
     * @return An instance of [JetWhaleHostPlugin].
     */
    public fun createPlugin(context: JetWhaleHostPluginContext): JetWhaleHostPlugin
}
