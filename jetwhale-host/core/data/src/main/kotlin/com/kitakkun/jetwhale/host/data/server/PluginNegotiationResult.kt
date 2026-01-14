package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo

data class PluginNegotiationResult(
    val requestedPlugins: List<JetWhalePluginInfo>,
)
