package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo

@JvmInline
value class PluginNegotiationResult(
    val requestedPlugins: List<JetWhalePluginInfo>,
)
