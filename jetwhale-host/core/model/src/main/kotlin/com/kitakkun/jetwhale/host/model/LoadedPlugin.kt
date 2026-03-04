package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory

data class LoadedPlugin(
    val factory: JetWhaleHostPluginFactory,
    val pluginId: String,
    val pluginName: String,
    val version: String,
    val author: String? = null,
    val description: String? = null,
    val activeIconPath: String? = null,
    val inactiveIconPath: String? = null,
)
