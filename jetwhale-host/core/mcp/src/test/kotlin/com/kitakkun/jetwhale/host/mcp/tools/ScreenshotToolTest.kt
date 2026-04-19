package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.mcp.viewport.McpViewport
import com.kitakkun.jetwhale.host.mcp.viewport.applyViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class ScreenshotToolTest {

    @Test
    fun `captureScreenshot returns non-empty PNG bytes on empty scene`() {
        val scene = createTestScene()
        val viewport = McpViewport(size = IntSize(320, 240), density = Density(1f))

        val bytes = captureScreenshot(scene, viewport)

        assertTrue(bytes.isNotEmpty(), "Expected PNG bytes to be non-empty")
        // PNG magic number: 0x89 0x50 0x4E 0x47
        assertTrue(bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte(), "Expected PNG header")
    }

    @Test
    fun `captureScreenshot returns PNG with correct dimensions`() {
        val scene = createTestScene {
            Box(modifier = Modifier.size(100.dp).background(Color.Red))
        }
        scene.composeScene.size = IntSize(320, 240)
        val viewport = McpViewport(size = IntSize(320, 240), density = Density(1f))

        val bytes = captureScreenshot(scene, viewport)

        assertTrue(bytes.isNotEmpty())
        // PNG width (bytes 16-19) and height (bytes 20-23) are big-endian int32
        val width = (bytes[16].toInt() and 0xFF shl 24) or (bytes[17].toInt() and 0xFF shl 16) or
            (bytes[18].toInt() and 0xFF shl 8) or (bytes[19].toInt() and 0xFF)
        val height = (bytes[20].toInt() and 0xFF shl 24) or (bytes[21].toInt() and 0xFF shl 16) or
            (bytes[22].toInt() and 0xFF shl 8) or (bytes[23].toInt() and 0xFF)
        assertEquals(320, width, "PNG width should match viewport width")
        assertEquals(240, height, "PNG height should match viewport height")
    }

    @Test
    fun `captureScreenshot handles different viewport sizes`() {
        val scene = createTestScene()

        for ((width, height) in listOf(160 to 120, 640 to 480, 1280 to 720)) {
            val viewport = McpViewport(size = IntSize(width, height), density = Density(1f))
            val bytes = captureScreenshot(scene, viewport)
            assertTrue(bytes.isNotEmpty(), "Expected non-empty PNG for ${width}x$height")
        }
    }

    @Test
    fun `captureScreenshot renders colors of side-by-side boxes correctly`() {
        // Two 50x50 boxes: red on the left, blue on the right
        val scene = createTestScene {
            Row {
                Box(modifier = Modifier.size(50.dp).background(Color.Red))
                Box(modifier = Modifier.size(50.dp).background(Color.Blue))
            }
        }
        val viewport = McpViewport(size = IntSize(100, 50), density = Density(1f))
        applyViewport(scene, viewport)

        val imageBitmap = ImageBitmap(100, 50)
        scene.composeScene.render(Canvas(imageBitmap), System.nanoTime())
        val pixels = imageBitmap.toPixelMap()

        // Sample the center of each box
        assertEquals(Color.Red, pixels[25, 25], "Expected red at center of left box")
        assertEquals(Color.Blue, pixels[75, 25], "Expected blue at center of right box")
    }
}
