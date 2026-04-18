package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TypeToolTest {

    @Test
    fun `specialKeyToComposeKey returns correct key for ENTER`() {
        assertEquals(Key.Enter, specialKeyToComposeKey("ENTER"))
    }

    @Test
    fun `specialKeyToComposeKey returns correct key for BACKSPACE`() {
        assertEquals(Key.Backspace, specialKeyToComposeKey("BACKSPACE"))
    }

    @Test
    fun `specialKeyToComposeKey returns correct key for DELETE`() {
        assertEquals(Key.Delete, specialKeyToComposeKey("DELETE"))
    }

    @Test
    fun `specialKeyToComposeKey returns correct key for TAB`() {
        assertEquals(Key.Tab, specialKeyToComposeKey("TAB"))
    }

    @Test
    fun `specialKeyToComposeKey returns correct key for ESCAPE`() {
        assertEquals(Key.Escape, specialKeyToComposeKey("ESCAPE"))
    }

    @Test
    fun `specialKeyToComposeKey returns correct key for arrow keys`() {
        assertEquals(Key.DirectionUp, specialKeyToComposeKey("UP"))
        assertEquals(Key.DirectionDown, specialKeyToComposeKey("DOWN"))
        assertEquals(Key.DirectionLeft, specialKeyToComposeKey("LEFT"))
        assertEquals(Key.DirectionRight, specialKeyToComposeKey("RIGHT"))
    }

    @Test
    fun `specialKeyToComposeKey returns correct key for navigation keys`() {
        assertEquals(Key.MoveHome, specialKeyToComposeKey("HOME"))
        assertEquals(Key.MoveEnd, specialKeyToComposeKey("END"))
        assertEquals(Key.PageUp, specialKeyToComposeKey("PAGE_UP"))
        assertEquals(Key.PageDown, specialKeyToComposeKey("PAGE_DOWN"))
    }

    @Test
    fun `specialKeyToComposeKey is case-insensitive`() {
        assertEquals(Key.Enter, specialKeyToComposeKey("enter"))
        assertEquals(Key.Tab, specialKeyToComposeKey("Tab"))
        assertEquals(Key.Escape, specialKeyToComposeKey("Escape"))
    }

    @Test
    fun `specialKeyToComposeKey returns null for unknown key`() {
        assertNull(specialKeyToComposeKey("UNKNOWN_KEY"))
        assertNull(specialKeyToComposeKey(""))
        assertNull(specialKeyToComposeKey("F1"))
    }

    @Test
    fun `specialKeyToComposeKey covers all documented keys`() {
        val keys = listOf(
            "ENTER", "BACKSPACE", "DELETE", "TAB", "ESCAPE",
            "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PAGE_UP", "PAGE_DOWN",
        )
        for (key in keys) {
            assertNotNull(specialKeyToComposeKey(key), "Expected non-null for key: $key")
        }
    }
}
