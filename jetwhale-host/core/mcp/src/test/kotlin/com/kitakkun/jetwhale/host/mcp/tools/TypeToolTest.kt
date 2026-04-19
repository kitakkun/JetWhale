package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun `dispatchTyping returns false when no text field is present`() {
        val scene = createTestScene()
        scene.composeScene.size = IntSize(1280, 720)
        scene.composeScene.render(Canvas(ImageBitmap(1280, 720)), System.nanoTime())

        val result = dispatchTyping(scene, "hello")

        assertFalse(result, "Expected false when no editable text field is in the scene")
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun `dispatchTyping inserts text into a text field`() {
        val textState = TextFieldState()
        val scene = createTestScene {
            BasicTextField(state = textState, modifier = Modifier.size(200.dp))
        }
        scene.composeScene.size = IntSize(1280, 720)
        scene.composeScene.render(Canvas(ImageBitmap(1280, 720)), System.nanoTime())

        val result = dispatchTyping(scene, "hello")

        assertTrue(result, "Expected true when a text field is present")
        assertEquals("hello", textState.text.toString())
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun `dispatchTyping appends text on successive calls`() {
        val textState = TextFieldState()
        val scene = createTestScene {
            BasicTextField(state = textState, modifier = Modifier.size(200.dp))
        }
        scene.composeScene.size = IntSize(1280, 720)
        scene.composeScene.render(Canvas(ImageBitmap(1280, 720)), System.nanoTime())

        dispatchTyping(scene, "foo")
        dispatchTyping(scene, "bar")

        assertEquals("foobar", textState.text.toString())
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun `dispatchSpecialKey BACKSPACE deletes last character`() {
        val textState = TextFieldState()
        val scene = createTestScene {
            BasicTextField(state = textState, modifier = Modifier.size(200.dp))
        }
        scene.composeScene.size = IntSize(1280, 720)
        scene.composeScene.render(Canvas(ImageBitmap(1280, 720)), System.nanoTime())

        dispatchTyping(scene, "hello")
        assertEquals("hello", textState.text.toString())

        // Focus the text field so it can receive key events
        fun findFocusableNode(node: SemanticsNode): SemanticsNode? {
            node.children.forEach { child -> findFocusableNode(child)?.let { return it } }
            return if (node.config.getOrNull(SemanticsActions.RequestFocus) != null) node else null
        }

        val rootNodes = scene.semanticsOwners.map { it.rootSemanticsNode }
        val target = rootNodes.firstNotNullOfOrNull { findFocusableNode(it) }
        checkNotNull(target) { "No focusable node found" }
        target.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()

        dispatchSpecialKey(scene, Key.Backspace)

        assertEquals("hell", textState.text.toString())
    }
}
