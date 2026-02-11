package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggeeEvent
import com.kitakkun.jetwhale.protocol.core.JetWhaleDebuggerEvent
import kotlinx.coroutines.flow.Flow

internal data class JetWhaleConnection(
    val negotiationResult: ClientSessionNegotiationResult.Success,
    val debuggerEventFlow: Flow<JetWhaleDebuggerEvent>,
)

internal interface JetWhaleSocketClient {
    suspend fun sendDebuggeeEvent(event: JetWhaleDebuggeeEvent)
    suspend fun openConnection(host: String, port: Int): JetWhaleConnection
}
