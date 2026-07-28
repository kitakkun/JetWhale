package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ClosedSessionsTest {
    @Test
    fun `a session that disappeared is reported as closed`() {
        val closed = closedSessions(
            previous = listOf(session("session-1"), session("session-2")),
            current = listOf(session("session-2")),
        )

        assertEquals(listOf("session-1"), closed.map { it.id })
    }

    /**
     * The disconnect must be announced once. Re-reporting on every later session-list update is what
     * made a long-gone session pop up again each time another session connected or closed.
     */
    @Test
    fun `a session already gone from the previous list is not reported again`() {
        val closed = closedSessions(
            previous = listOf(session("session-2")),
            current = listOf(session("session-2"), session("session-3")),
        )

        assertEquals(emptyList(), closed)
    }

    @Test
    fun `sessions that disappeared together are reported together`() {
        val closed = closedSessions(
            previous = listOf(session("session-1"), session("session-2")),
            current = emptyList(),
        )

        assertEquals(listOf("session-1", "session-2"), closed.map { it.id })
    }

    @Test
    fun `the first session list reports nothing as closed`() {
        val closed = closedSessions(
            previous = emptyList(),
            current = listOf(session("session-1")),
        )

        assertEquals(emptyList(), closed)
    }

    private fun session(id: String) = DebugSession(
        id = id,
        name = id,
        transportSecurity = SessionTransportSecurity.PLAINTEXT,
        installedPlugins = persistentListOf(),
    )
}
