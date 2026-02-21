package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

interface DebugSessionRepository {
    val debugSessionsFlow: Flow<ImmutableList<DebugSession>>

    suspend fun registerDebugSession(
        sessionId: String,
        sessionName: String?,
        installedPlugins: List<JetWhalePluginInfo>,
    )

    fun unregisterDebugSession(sessionId: String)
    fun markAllSessionsInactive()
}
