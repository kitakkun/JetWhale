package com.kitakkun.jetwhale.host.data.plugin

import kotlinx.serialization.Serializable

@Serializable
internal data class JetWhalePluginJson(
    val pluginId: String,
    val pluginName: String,
    val version: String,
    val author: String? = null,
    val description: String? = null,
    val activeIconPath: String? = null,
    val inactiveIconPath: String? = null,
)
