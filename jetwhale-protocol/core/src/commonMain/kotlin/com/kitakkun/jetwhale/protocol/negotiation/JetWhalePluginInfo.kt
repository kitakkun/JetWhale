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
public data class JetWhalePluginInfo(
    val pluginId: String,
    val pluginVersion: String,
)
