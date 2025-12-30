package com.kitakkun.jetwhale.debugger.host.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

interface DebugSessionRepository {
    val debugSessionsFlow: Flow<ImmutableList<DebugSession>>

    suspend fun registerDebugSession(sessionId: String, sessionName: String?)
    fun unregisterDebugSession(sessionId: String)
}
