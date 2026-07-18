package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAppMetadata
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

interface DebugSessionRepository {
    val debugSessionsFlow: Flow<ImmutableList<DebugSession>>

    suspend fun registerDebugSession(
        sessionId: String,
        sessionName: String?,
        transportSecurity: SessionTransportSecurity,
        installedPlugins: List<JetWhalePluginInfo>,
        appMetadata: JetWhaleAppMetadata,
    )

    fun unregisterDebugSession(sessionId: String)
    fun markAllSessionsInactive()
}
