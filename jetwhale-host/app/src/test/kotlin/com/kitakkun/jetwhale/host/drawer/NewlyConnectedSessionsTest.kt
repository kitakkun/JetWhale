package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewlyConnectedSessionsTest {
    @Test
    fun `a session that appeared active is reported as connected`() {
        val connected = newlyConnectedSessions(previouslyConnected = listOf(session("a")), current = listOf(session("a"), session("b")))

        assertEquals(listOf("b"), connected.map { it.id })
    }

    @Test
    fun `a session already connected before this update is not reported again`() {
        val connected = newlyConnectedSessions(previouslyConnected = listOf(session("a")), current = listOf(session("a")))

        assertTrue(connected.isEmpty(), "a was connected before this update and must not be re-reported")
    }

    @Test
    fun `a session that reconnects is reported again`() {
        // The disconnected entry stays in the list, so the diff is on "connected", not on "present".
        val connected = newlyConnectedSessions(previouslyConnected = emptyList(), current = listOf(session("a")))

        assertEquals(listOf("a"), connected.map { it.id })
    }

    @Test
    fun `a session that only appeared inactive is not reported`() {
        val connected = newlyConnectedSessions(previouslyConnected = emptyList(), current = listOf(session("a", isActive = false)))

        assertTrue(connected.isEmpty(), "an inactive entry is not an arrival")
    }

    @Test
    fun `sessions connecting together are all reported`() {
        val connected = newlyConnectedSessions(
            previouslyConnected = listOf(session("a")),
            current = listOf(session("a"), session("b"), session("c")),
        )

        assertEquals(listOf("b", "c"), connected.map { it.id })
    }

    @Test
    fun `sessions present from the start report nothing`() {
        // The presenter seeds the previous snapshot from the first list, so opening the window is silent.
        val initial = listOf(session("a"), session("b"))

        val connected = newlyConnectedSessions(previouslyConnected = initial, current = initial)

        assertTrue(connected.isEmpty(), "sessions present from the start have not just connected")
    }

    private fun session(id: String, isActive: Boolean = true) = DebugSession(
        id = id,
        name = id,
        isActive = isActive,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(),
    )
}
