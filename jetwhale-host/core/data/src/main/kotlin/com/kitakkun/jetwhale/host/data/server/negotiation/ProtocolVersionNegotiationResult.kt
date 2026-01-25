package com.kitakkun.jetwhale.host.data.server.negotiation

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleProtocolVersion

sealed interface ProtocolVersionNegotiationResult {
    data class Success(val negotiatedVersion: JetWhaleProtocolVersion) : ProtocolVersionNegotiationResult
    data object Failure : ProtocolVersionNegotiationResult
}
