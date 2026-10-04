package com.kitakkun.jetwhale.plugins.permissions.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeDenialTest {
    @Test
    fun `a rationale means the permission was denied once`() {
        assertEquals(RuntimeDenial.Once, RuntimeDenial.Unknown.after(rationale = true))
        assertEquals(RuntimeDenial.Once, RuntimeDenial.Once.after(rationale = true))
    }

    @Test
    fun `losing the rationale after a denial means the denial is permanent`() {
        assertEquals(RuntimeDenial.Permanently, RuntimeDenial.Once.after(rationale = false))
        assertEquals(RuntimeDenial.Permanently, RuntimeDenial.Permanently.after(rationale = false))
    }

    @Test
    fun `no rationale before any denial is seen leaves never asked and permanent untold`() {
        assertEquals(RuntimeDenial.Unknown, RuntimeDenial.Unknown.after(rationale = false))
    }

    @Test
    fun `the last answer stands while no activity can be asked`() {
        assertEquals(RuntimeDenial.Once, RuntimeDenial.Once.after(rationale = null))
        assertEquals(RuntimeDenial.Permanently, RuntimeDenial.Permanently.after(rationale = null))
    }

    @Test
    fun `only a permanent denial makes a permission no longer requestable`() {
        assertTrue(RuntimeDenial.Unknown.requestable)
        assertTrue(RuntimeDenial.Once.requestable)
        assertFalse(RuntimeDenial.Permanently.requestable)
    }
}
