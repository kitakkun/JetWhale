package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rendered density decides how many dp fit in a requested pixel size, so it changes what a
 * screenshot actually shows. Callers need to be able to pin it — otherwise the same request renders
 * differently on a HiDPI machine than on a plain one.
 */
@OptIn(InternalComposeUiApi::class)
class ScreenshotViewportTest {

    @Test
    fun `an explicit density overrides the scene's own`() = runBlocking {
        withContext(Dispatchers.Main) {
            val scene = createTestScene().also(::renderTestScene)

            val viewport = resolveViewport(scene, requestedWidth = null, requestedHeight = null, requestedDensity = 3f)

            assertEquals(3f, viewport.density.density)
        }
    }

    @Test
    fun `omitting density keeps the scene's own`() = runBlocking {
        withContext(Dispatchers.Main) {
            val scene = createTestScene().also(::renderTestScene)
            val sceneDensity = scene.composeScene.density.density

            val viewport = resolveViewport(scene, requestedWidth = null, requestedHeight = null, requestedDensity = null)

            assertEquals(sceneDensity, viewport.density.density)
        }
    }

    @Test
    fun `density and size are resolved independently`() = runBlocking {
        withContext(Dispatchers.Main) {
            val scene = createTestScene().also(::renderTestScene)

            val viewport = resolveViewport(scene, requestedWidth = 800, requestedHeight = 600, requestedDensity = 2f)

            assertEquals(IntSize(800, 600), viewport.size)
            assertEquals(2f, viewport.density.density)
        }
    }

    @Test
    fun `omitting size falls back to the scene's current size`() = runBlocking {
        withContext(Dispatchers.Main) {
            val scene = createTestScene().also(::renderTestScene)

            val viewport = resolveViewport(scene, requestedWidth = null, requestedHeight = null, requestedDensity = 2f)

            assertEquals(IntSize(TEST_SCENE_WIDTH, TEST_SCENE_HEIGHT), viewport.size)
        }
    }
}
