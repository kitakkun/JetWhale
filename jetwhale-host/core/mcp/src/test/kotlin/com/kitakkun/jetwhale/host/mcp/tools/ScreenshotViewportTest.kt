package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @Test
    fun `overriding density keeps the scene's font scale`() = runBlocking {
        withContext(Dispatchers.Main) {
            val scene = createTestScene().also(::renderTestScene)
            scene.composeScene.density = Density(density = 1f, fontScale = 1.5f)

            val viewport = resolveViewport(scene, requestedWidth = null, requestedHeight = null, requestedDensity = 2f)

            assertEquals(2f, viewport.density.density)
            assertEquals(1.5f, viewport.density.fontScale)
        }
    }

    @Test
    fun `a usable density is accepted`() {
        assertNull(invalidDensityMessage(null))
        assertNull(invalidDensityMessage(1f))
        assertNull(invalidDensityMessage(2.5f))
    }

    @Test
    fun `a non-positive density is rejected`() {
        assertNotNull(invalidDensityMessage(0f))
        assertNotNull(invalidDensityMessage(-1f))
    }

    @Test
    fun `a non-finite density is rejected`() {
        // A JSON number too large for a Float parses to Infinity, and NaN parses to NaN. Neither is
        // caught by a `<= 0` test, so both would otherwise reach Compose layout.
        assertNotNull(invalidDensityMessage("1e400".toFloat()))
        assertNotNull(invalidDensityMessage(Float.POSITIVE_INFINITY))
        assertNotNull(invalidDensityMessage(Float.NEGATIVE_INFINITY))
        assertNotNull(invalidDensityMessage(Float.NaN))
    }
}
