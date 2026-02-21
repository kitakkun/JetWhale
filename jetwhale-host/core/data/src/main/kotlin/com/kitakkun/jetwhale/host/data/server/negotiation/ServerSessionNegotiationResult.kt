package com.kitakkun.jetwhale.host.data.server.negotiation

sealed interface ServerSessionNegotiationResult {
    data class Success(
        val session: SessionNegotiationResult,
        val plugin: PluginNegotiationResult,
    ) : ServerSessionNegotiationResult

    data class Failure(val error: Throwable) : ServerSessionNegotiationResult
}
