package com.kitakkun.jetwhale.host.mcp.tools

import java.awt.event.KeyEvent as AwtKeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TypeToolTest {

    @Test
    fun `specialKeyCode returns correct code for ENTER`() {
        assertEquals(AwtKeyEvent.VK_ENTER, specialKeyCode("ENTER"))
    }

    @Test
    fun `specialKeyCode returns correct code for BACKSPACE`() {
        assertEquals(AwtKeyEvent.VK_BACK_SPACE, specialKeyCode("BACKSPACE"))
    }

    @Test
    fun `specialKeyCode returns correct code for DELETE`() {
        assertEquals(AwtKeyEvent.VK_DELETE, specialKeyCode("DELETE"))
    }

    @Test
    fun `specialKeyCode returns correct code for TAB`() {
        assertEquals(AwtKeyEvent.VK_TAB, specialKeyCode("TAB"))
    }

    @Test
    fun `specialKeyCode returns correct code for ESCAPE`() {
        assertEquals(AwtKeyEvent.VK_ESCAPE, specialKeyCode("ESCAPE"))
    }

    @Test
    fun `specialKeyCode returns correct code for arrow keys`() {
        assertEquals(AwtKeyEvent.VK_UP, specialKeyCode("UP"))
        assertEquals(AwtKeyEvent.VK_DOWN, specialKeyCode("DOWN"))
        assertEquals(AwtKeyEvent.VK_LEFT, specialKeyCode("LEFT"))
        assertEquals(AwtKeyEvent.VK_RIGHT, specialKeyCode("RIGHT"))
    }

    @Test
    fun `specialKeyCode returns correct code for navigation keys`() {
        assertEquals(AwtKeyEvent.VK_HOME, specialKeyCode("HOME"))
        assertEquals(AwtKeyEvent.VK_END, specialKeyCode("END"))
        assertEquals(AwtKeyEvent.VK_PAGE_UP, specialKeyCode("PAGE_UP"))
        assertEquals(AwtKeyEvent.VK_PAGE_DOWN, specialKeyCode("PAGE_DOWN"))
    }

    @Test
    fun `specialKeyCode is case-insensitive`() {
        assertEquals(AwtKeyEvent.VK_ENTER, specialKeyCode("enter"))
        assertEquals(AwtKeyEvent.VK_TAB, specialKeyCode("Tab"))
        assertEquals(AwtKeyEvent.VK_ESCAPE, specialKeyCode("Escape"))
    }

    @Test
    fun `specialKeyCode returns null for unknown key`() {
        assertNull(specialKeyCode("UNKNOWN_KEY"))
        assertNull(specialKeyCode(""))
        assertNull(specialKeyCode("F1"))
    }

    @Test
    fun `specialKeyCode covers all documented keys`() {
        val keys = listOf(
            "ENTER", "BACKSPACE", "DELETE", "TAB", "ESCAPE",
            "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PAGE_UP", "PAGE_DOWN",
        )
        for (key in keys) {
            assertNotNull(specialKeyCode(key), "Expected non-null for key: $key")
        }
    }
}
