package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleProtocolVersion

data class ProtocolVersionNegotiationResult(
    val negotiatedVersion: JetWhaleProtocolVersion,
)
