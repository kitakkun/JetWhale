package com.kitakkun.jetwhale.host.sdk

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalJetWhaleHostApi::class)
class JetWhaleHostPluginTest {
    @Test
    fun `an instance bound to the host session knows it runs there`() {
        val plugin = SessionKindProbePlugin().apply { bindSessionKind(isInHostSession = true) }

        assertTrue(plugin.readIsInHostSession())
    }

    @Test
    fun `an instance bound to an app session knows it does not run in the host session`() {
        val plugin = SessionKindProbePlugin().apply { bindSessionKind(isInHostSession = false) }

        assertFalse(plugin.readIsInHostSession())
    }

    @Test
    fun `isInHostSession before the host binds it says when it becomes available`() {
        val error = assertFailsWith<IllegalStateException> { SessionKindProbePlugin().readIsInHostSession() }

        assertContains(error.message.orEmpty(), "onCreate")
    }

    private class SessionKindProbePlugin : JetWhaleHostPlugin() {
        fun readIsInHostSession(): Boolean = isInHostSession
    }
}
