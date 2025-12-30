package com.kitakkun.jetwhale.debugger.host.sdk

public class JetWhalePluginMetaData internal constructor(
    public val pluginId: String,
    public val pluginName: String,
    public val version: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JetWhalePluginMetaData) return false

        if (pluginId != other.pluginId) return false
        if (pluginName != other.pluginName) return false
        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pluginId.hashCode()
        result = 31 * result + pluginName.hashCode()
        result = 31 * result + version.hashCode()
        return result
    }
}

public fun jetWhalePluginMetaData(
    pluginId: String,
    pluginName: String,
    version: String,
): JetWhalePluginMetaData = JetWhalePluginMetaData(
    pluginId = pluginId,
    pluginName = pluginName,
    version = version,
)
