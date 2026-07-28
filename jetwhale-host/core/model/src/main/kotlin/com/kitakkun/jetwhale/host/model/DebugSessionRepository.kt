package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAppMetadata
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * Holds the debug sessions currently connected to the host. A session lives only as long as its
 * connection does: disconnecting drops it instead of keeping it as history, so nothing here grows
 * with the number of connect/disconnect cycles.
 */
interface DebugSessionRepository {
    val debugSessionsFlow: Flow<ImmutableList<DebugSession>>

    suspend fun registerDebugSession(
        sessionId: String,
        sessionName: String?,
        transportSecurity: SessionTransportSecurity,
        installedPlugins: List<JetWhalePluginInfo>,
        appMetadata: JetWhaleAppMetadata,
    )

    /** Drops the session with [sessionId]. Unknown ids are ignored. */
    fun unregisterDebugSession(sessionId: String)

    /** Drops every session, for when the server stops and no agent can still be attached. */
    fun unregisterAllDebugSessions()
}
