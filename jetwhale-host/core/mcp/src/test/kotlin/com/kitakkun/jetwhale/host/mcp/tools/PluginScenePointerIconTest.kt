package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A plugin renders into a nested, windowless [androidx.compose.ui.scene.ComposeScene], so its
 * `Modifier.pointerHoverIcon` cannot touch a cursor by itself — the request only reaches the host
 * through `PlatformContext.setPointerIcon`, whose default implementation is a no-op. These tests pin
 * that routing, which is what a Compose upgrade could silently take away.
 */
@OptIn(InternalComposeUiApi::class)
class PluginScenePointerIconTest {

    @Test
    fun `hovering a pointerHoverIcon region publishes the requested icon`() = runBlocking {
        withContext(Dispatchers.Main) {
            val scene = hoverIconScene()

            scene.composeScene.sendPointerEvent(PointerEventType.Move, Offset(50f, 50f))

            assertEquals(PointerIcon.Hand, scene.pointerIcon.value)
        }
    }

    @Test
    fun `a scene with no hovered region reports the default icon`() = runBlocking {
        withContext(Dispatchers.Main) {
            val scene = hoverIconScene()

            assertEquals(PointerIcon.Default, scene.pointerIcon.value)
        }
    }

    @Test
    fun `leaving the region restores the default icon`() = runBlocking {
        withContext(Dispatchers.Main) {
            val scene = hoverIconScene()
            scene.composeScene.sendPointerEvent(PointerEventType.Move, Offset(50f, 50f))

            scene.composeScene.sendPointerEvent(PointerEventType.Move, Offset(400f, 400f))

            assertEquals(PointerIcon.Default, scene.pointerIcon.value)
        }
    }

    /** A rendered scene whose top-left 200x200 region asks for [PointerIcon.Hand]. */
    private fun hoverIconScene() = createTestScene {
        Box(Modifier.size(200.dp).pointerHoverIcon(PointerIcon.Hand))
    }.also(::renderTestScene)
}
