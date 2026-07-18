package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAppMetadata

data class SessionNegotiationResult(
    val sessionId: String,
    val sessionName: String,
    val appMetadata: JetWhaleAppMetadata,
)
