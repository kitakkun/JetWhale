package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationTargetSessionTest {
    private val live = session("live", isActive = true)
    private val closed = session("closed", isActive = false)

    @Test
    fun `a named session that is connected is the target`() {
        assertEquals(live, navigationTargetSession("live", selectedSession = null, sessions = listOf(live, closed)))
    }

    @Test
    fun `a named session that disconnected after validation drops the request`() {
        assertNull(navigationTargetSession("closed", selectedSession = live, sessions = listOf(live, closed)))
    }

    @Test
    fun `a named session that is gone drops the request`() {
        assertNull(navigationTargetSession("missing", selectedSession = live, sessions = listOf(live)))
    }

    @Test
    fun `a request without a session goes to the selected one`() {
        assertEquals(live, navigationTargetSession(null, selectedSession = live, sessions = listOf(live, closed)))
    }

    private fun session(id: String, isActive: Boolean) = DebugSession(
        id = id,
        name = id,
        isActive = isActive,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(),
    )
}
