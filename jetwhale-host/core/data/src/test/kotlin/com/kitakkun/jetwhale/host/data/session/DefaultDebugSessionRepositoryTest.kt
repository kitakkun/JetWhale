package com.kitakkun.jetwhale.host.data.session

import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.protocol.negotiation.JetWhaleAppMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultDebugSessionRepositoryTest {
    @Test
    fun `registered sessions are exposed through the flow`() = runBlocking {
        val repository = DefaultDebugSessionRepository()

        repository.register("session-1")
        repository.register("session-2")

        assertEquals(setOf("session-1", "session-2"), repository.sessionIds())
    }

    /**
     * Reconnecting always yields a fresh session id, so keeping disconnected sessions would make the
     * repository grow with every connect/disconnect cycle for the lifetime of the host process.
     */
    @Test
    fun `unregistering a session drops it instead of keeping it as history`() = runBlocking {
        val repository = DefaultDebugSessionRepository()

        repository.register("session-1")
        repository.register("session-2")
        repository.unregisterDebugSession("session-1")

        assertEquals(setOf("session-2"), repository.sessionIds())
    }

    @Test
    fun `unregistering an unknown session leaves the known ones untouched`() = runBlocking {
        val repository = DefaultDebugSessionRepository()

        repository.register("session-1")
        repository.unregisterDebugSession("session-unknown")

        assertEquals(setOf("session-1"), repository.sessionIds())
    }

    @Test
    fun `unregistering all sessions empties the repository`() = runBlocking {
        val repository = DefaultDebugSessionRepository()

        repository.register("session-1")
        repository.register("session-2")
        repository.unregisterAllDebugSessions()

        assertEquals(emptySet(), repository.sessionIds())
    }

    private suspend fun DefaultDebugSessionRepository.register(sessionId: String) {
        registerDebugSession(
            sessionId = sessionId,
            sessionName = sessionId,
            transportSecurity = SessionTransportSecurity.PLAINTEXT,
            installedPlugins = emptyList(),
            appMetadata = JetWhaleAppMetadata(),
        )
    }

    private suspend fun DefaultDebugSessionRepository.sessionIds(): Set<String> = debugSessionsFlow.first().mapTo(mutableSetOf()) { it.id }
}
