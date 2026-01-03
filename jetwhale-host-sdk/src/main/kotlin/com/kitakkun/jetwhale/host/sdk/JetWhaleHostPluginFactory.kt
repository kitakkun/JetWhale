package com.kitakkun.jetwhale.host.sdk

public interface JetWhaleHostPluginFactory {
    public val meta: JetWhalePluginMetaData
    public val icon: JetWhalePluginIcon get() = unspecifiedPluginIcon()

    public fun createPlugin(): JetWhaleRawHostPlugin
}
