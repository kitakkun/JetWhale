package com.kitakkun.jetwhale.host.data.server

sealed interface ServerSessionNegotiationResult {
    data class Success(
        val sessionId: String,
        val sessionName: String
    ) : ServerSessionNegotiationResult

    data object Failure : ServerSessionNegotiationResult
}
