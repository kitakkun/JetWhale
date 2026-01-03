package com.kitakkun.jetwhale.host.sdk

public class JetWhalePluginIcon internal constructor(
    public val activeIconPath: String?,
    public val inactiveIconPath: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JetWhalePluginIcon) return false

        if (activeIconPath != other.activeIconPath) return false
        if (inactiveIconPath != other.inactiveIconPath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = activeIconPath.hashCode()
        result = 31 * result + inactiveIconPath.hashCode()
        return result
    }
}

public fun unspecifiedPluginIcon(): JetWhalePluginIcon = JetWhalePluginIcon(
    activeIconPath = null,
    inactiveIconPath = null,
)

public fun pluginIcon(
    activeIconPath: String?,
    inactiveIconPath: String?,
): JetWhalePluginIcon = JetWhalePluginIcon(
    activeIconPath = activeIconPath,
    inactiveIconPath = inactiveIconPath,
)
