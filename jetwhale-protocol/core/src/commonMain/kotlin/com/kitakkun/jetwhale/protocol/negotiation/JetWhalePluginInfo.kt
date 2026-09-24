package com.kitakkun.jetwhale.protocol.negotiation

import com.kitakkun.jetwhale.protocol.JetWhaleSerialNames
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Information about a JetWhale plugin.
 *
 * @param pluginId The unique identifier of the plugin.
 * @param pluginVersion The version of the plugin.
 */
@SerialName(JetWhaleSerialNames.MODEL_PLUGIN_INFO)
@Serializable
public class JetWhalePluginInfo(
    public val pluginId: String,
    public val pluginVersion: String,
) {
    override fun equals(other: Any?): Boolean = other is JetWhalePluginInfo && pluginId == other.pluginId && pluginVersion == other.pluginVersion

    override fun hashCode(): Int = 31 * pluginId.hashCode() + pluginVersion.hashCode()

    override fun toString(): String = "JetWhalePluginInfo(pluginId=$pluginId, pluginVersion=$pluginVersion)"
}
