package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo

sealed interface ServerSessionNegotiationResult {
    data class Success(
        val sessionId: String,
        val sessionName: String,
        val installedPlugins: List<JetWhalePluginInfo>,
    ) : ServerSessionNegotiationResult

    data object Failure : ServerSessionNegotiationResult
}
