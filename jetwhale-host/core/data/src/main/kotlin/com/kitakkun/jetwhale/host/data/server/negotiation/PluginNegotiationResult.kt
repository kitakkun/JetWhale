package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo

data class PluginNegotiationResult(
    val requestedPlugins: List<JetWhalePluginInfo>,
)
