package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.flow.Flow

internal data class JetWhaleConnection(
    val negotiationResult: ClientSessionNegotiationResult.Success,
    val messageFlow: Flow<String>,
)

internal interface JetWhaleSocketClient {
    suspend fun sendMessage(pluginId: String, message: String)
    suspend fun openConnection(host: String, port: Int): JetWhaleConnection
}
