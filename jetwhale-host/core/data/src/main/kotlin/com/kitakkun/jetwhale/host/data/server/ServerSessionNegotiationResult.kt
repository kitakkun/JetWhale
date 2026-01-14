package com.kitakkun.jetwhale.host.data.server

sealed interface ServerSessionNegotiationResult {
    data class Success(
        val session: SessionNegotiationResult,
        val plugin: PluginNegotiationResult,
    ) : ServerSessionNegotiationResult

    data object Failure : ServerSessionNegotiationResult
}
