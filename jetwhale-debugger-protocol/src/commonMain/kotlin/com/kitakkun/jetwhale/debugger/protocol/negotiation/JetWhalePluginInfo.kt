package com.kitakkun.jetwhale.debugger.protocol.negotiation

import kotlinx.serialization.Serializable

/**
 * Information about a JetWhale plugin.
 *
 * @param pluginId The unique identifier of the plugin.
 * @param pluginVersion The version of the plugin.
 */
@Serializable
data class JetWhalePluginInfo(
    val pluginId: String,
    val pluginVersion: String,
)
