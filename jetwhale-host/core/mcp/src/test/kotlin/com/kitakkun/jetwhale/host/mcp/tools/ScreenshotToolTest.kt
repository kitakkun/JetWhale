package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.mcp.viewport.McpViewport
import androidx.compose.ui.unit.Density
import kotlin.test.Test
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
    }

    @Test
    fun `captureScreenshot handles different viewport sizes`() {
        val scene = createTestScene()

        for ((width, height) in listOf(160 to 120, 640 to 480, 1280 to 720)) {
            val viewport = McpViewport(size = IntSize(width, height), density = Density(1f))
            val bytes = captureScreenshot(scene, viewport)
            assertTrue(bytes.isNotEmpty(), "Expected non-empty PNG for ${width}x${height}")
        }
    }
}
