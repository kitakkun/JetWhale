package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo

sealed interface ServerSessionNegotiationResult {
    data class Success(
        val session: SessionNegotiationResult,
        val installedPlugins: List<JetWhalePluginInfo>,
    ) : ServerSessionNegotiationResult

    data object Failure : ServerSessionNegotiationResult
}
