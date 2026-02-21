package com.kitakkun.jetwhale.host.data.session

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultDebugSessionRepository : DebugSessionRepository {
    override val debugSessionsFlow: Flow<ImmutableList<DebugSession>> get() = mutableDebugSessions.map { it.values.toImmutableList() }
    private val mutableDebugSessions: MutableStateFlow<ImmutableMap<String, DebugSession>> = MutableStateFlow(persistentMapOf())

    override suspend fun registerDebugSession(
        sessionId: String,
        sessionName: String?,
        installedPlugins: List<JetWhalePluginInfo>,
    ) {
        mutableDebugSessions.update { sessions ->
            sessions.toMutableMap().apply {
                set(
                    sessionId,
                    DebugSession(
                        id = sessionId,
                        name = sessionName,
                        isActive = true,
                        installedPlugins = installedPlugins.toImmutableList(),
                    )
                )
            }.toPersistentMap()
        }
    }

    override fun unregisterDebugSession(sessionId: String) {
        mutableDebugSessions.update { sessions ->
            sessions.toMutableMap().apply {
                this[sessionId]?.let {
                    this[sessionId] = it.copy(isActive = false)
                }
            }.toPersistentMap()
        }
    }

    override fun markAllSessionsInactive() {
        mutableDebugSessions.update { sessions ->
            sessions.mapValues { (_, session) ->
                session.copy(isActive = false)
            }.toPersistentMap()
        }
    }
}
