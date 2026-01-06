package com.kitakkun.jetwhale.agent.runtime

internal sealed interface ClientSessionNegotiationResult {
    data object Success : ClientSessionNegotiationResult
    data class Failure(val reason: String) : ClientSessionNegotiationResult
}
