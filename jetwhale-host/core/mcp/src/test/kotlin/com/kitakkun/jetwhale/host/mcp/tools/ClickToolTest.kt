package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class ClickToolTest {

    @Test
    fun `dispatchClick returns false when scene has no clickable elements`() {
        val scene = createTestScene()
        // Render once to sync the semantics tree
        scene.composeScene.render(Canvas(ImageBitmap(1280, 720)), System.nanoTime())

        val result = dispatchClick(scene, 100f, 100f)
        assertFalse(result)
    }

    @Test
    fun `dispatchClick returns true when a clickable element is at the given position`() {
        var clicked = false
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clickable { clicked = true },
            )
        }
        // Render to build the semantics tree and lay out nodes
        scene.composeScene.size = androidx.compose.ui.unit.IntSize(1280, 720)
        scene.composeScene.render(Canvas(ImageBitmap(1280, 720)), System.nanoTime())

        val result = dispatchClick(scene, 100f, 100f)

        assertTrue(result, "Expected click to be dispatched to the clickable element")
        assertTrue(clicked, "Expected onClick callback to have been invoked")
    }

    @Test
    fun `dispatchClick returns false when coordinates are outside clickable element`() {
        var clicked = false
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clickable { clicked = true },
            )
        }
        scene.composeScene.size = androidx.compose.ui.unit.IntSize(1280, 720)
        scene.composeScene.render(Canvas(ImageBitmap(1280, 720)), System.nanoTime())

        // Element is 50dp x 50dp = 50px x 50px at density 1; click far outside
        val result = dispatchClick(scene, 500f, 500f)

        assertFalse(result)
        assertFalse(clicked)
    }
}
