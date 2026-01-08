package com.kitakkun.jetwhale.agent.runtime

internal sealed interface ClientSessionNegotiationResult {
    data class Success(val availablePluginIds: List<String>) : ClientSessionNegotiationResult
    data class Failure(val reason: String) : ClientSessionNegotiationResult
}
