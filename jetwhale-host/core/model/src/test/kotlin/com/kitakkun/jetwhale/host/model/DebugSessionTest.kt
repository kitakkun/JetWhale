package com.kitakkun.jetwhale.host.model

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DebugSessionTest {
    @Test
    fun `an app that reports its name is labeled with it`() {
        assertEquals("Shop", session(name = "Mac OS X", appName = "Shop").appDisplayName)
    }

    @Test
    fun `a session named after its device has no app label of its own`() {
        val session = session(name = "Mac OS X", appName = null)

        assertNull(session.appLabel)
        assertEquals("19a536", session.appDisplayName)
    }

    @Test
    fun `a session name that differs from the device still names the app`() {
        assertEquals("Checkout flow", session(name = "Checkout flow", appName = null).appLabel)
    }

    private fun session(name: String?, appName: String?) = DebugSession(
        id = "19a536-7c1d",
        name = name,
        isActive = true,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(),
        appName = appName,
        deviceId = null,
        deviceName = "Mac OS X",
    )
}
