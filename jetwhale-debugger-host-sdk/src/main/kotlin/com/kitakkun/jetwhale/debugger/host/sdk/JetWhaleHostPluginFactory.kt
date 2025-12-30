package com.kitakkun.jetwhale.debugger.host.sdk

public interface JetWhaleHostPluginFactory {
    public val meta: JetWhalePluginMetaData
    public val icon: JetWhalePluginIcon get() = unspecifiedPluginIcon()

    public fun createPlugin(): JetWhaleHostPlugin
}
