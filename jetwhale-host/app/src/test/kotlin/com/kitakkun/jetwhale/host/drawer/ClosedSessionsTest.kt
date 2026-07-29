package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClosedSessionsTest {
    @Test
    fun `a session marked inactive is reported as closed`() {
        val previous = listOf(session("a"), session("b"))

        val closed = closedSessions(previouslyConnected = previous, current = listOf(session("a"), session("b", isActive = false)))

        assertEquals(listOf("b"), closed.map { it.id })
    }

    @Test
    fun `a session already disconnected before this update is not reported again`() {
        // The disconnected session stays in the list, so only the previous snapshot can tell the
        // difference between "just went" and "went a while ago".
        val current = listOf(session("a"), session("b", isActive = false))

        val closed = closedSessions(previouslyConnected = listOf(session("a")), current = current)

        assertTrue(closed.isEmpty(), "b disconnected before this update and must not be re-reported")
    }

    @Test
    fun `a session that vanished from the list is reported as closed`() {
        val closed = closedSessions(previouslyConnected = listOf(session("a"), session("b")), current = listOf(session("a")))

        assertEquals(listOf("b"), closed.map { it.id })
    }

    @Test
    fun `sessions closing together are all reported`() {
        val previous = listOf(session("a"), session("b"), session("c"))

        val closed = closedSessions(
            previouslyConnected = previous,
            current = listOf(session("a", isActive = false), session("b", isActive = false), session("c")),
        )

        assertEquals(listOf("a", "b"), closed.map { it.id })
    }

    @Test
    fun `the first update reports nothing`() {
        val closed = closedSessions(previouslyConnected = emptyList(), current = listOf(session("a"), session("b")))

        assertTrue(closed.isEmpty(), "sessions present from the start have not closed")
    }

    private fun session(id: String, isActive: Boolean = true) = DebugSession(
        id = id,
        name = id,
        isActive = isActive,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(),
    )
}
