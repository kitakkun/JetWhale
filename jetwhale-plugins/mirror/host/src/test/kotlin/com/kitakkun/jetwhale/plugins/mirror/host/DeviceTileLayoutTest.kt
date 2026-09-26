package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PHONE = 0.46f
private const val TABLET = 0.75f

private val METRICS = TileMetrics(gap = 16f, tileExtraWidth = 12f, captionHeight = 56f, minScreenHeight = 220f, maxScreenHeight = 640f)

class DeviceTileLayoutTest {
    @Test
    fun `two phones in a wide pane sit side by side as tall as the pane allows`() {
        val layout = layoutDeviceTiles(listOf(PHONE, PHONE), width = 960f, height = 600f, metrics = METRICS)

        assertEquals(listOf(listOf(0, 1)), layout.rows)
        // The height is what limits them: the pane minus the caption.
        assertEquals(600f - 56f, layout.screenHeight)
        assertFits(layout, width = 960f, height = 600f)
    }

    @Test
    fun `tiles never grow past the largest size`() {
        val layout = layoutDeviceTiles(listOf(PHONE), width = 4000f, height = 3000f, metrics = METRICS)

        assertEquals(640f, layout.screenHeight)
        assertFalse(layout.scrolls)
    }

    @Test
    fun `many phones in a narrow pane stack into more rows before they shrink`() {
        val layout = layoutDeviceTiles(List(4) { PHONE }, width = 500f, height = 1400f, metrics = METRICS)

        assertTrue(layout.rows.size > 1, "one row of four would be tiny: ${layout.rows}")
        assertFits(layout, width = 500f, height = 1400f)
    }

    @Test
    fun `too many devices for the pane keep the readable size and scroll`() {
        val layout = layoutDeviceTiles(List(20) { PHONE }, width = 600f, height = 500f, metrics = METRICS)

        assertTrue(layout.scrolls)
        assertEquals(220f, layout.screenHeight)
        // Each scrolling row still fits the width.
        assertTrue(layout.groupWidth <= 600f, "a row is ${layout.groupWidth} wide")
        assertEquals((0 until 20).toList(), layout.rows.flatten())
    }

    @Test
    fun `a tablet and phones keep their own shapes on one screen height`() {
        val layout = layoutDeviceTiles(listOf(PHONE, TABLET, PHONE), width = 1400f, height = 700f, metrics = METRICS)

        assertFits(layout, width = 1400f, height = 700f)
        assertEquals(3, layout.rows.flatten().size)
    }

    @Test
    fun `the group is measured so it can be centered`() {
        val layout = layoutDeviceTiles(listOf(PHONE, PHONE), width = 2000f, height = 700f, metrics = METRICS)

        val tile = PHONE * layout.screenHeight + 12f
        assertEquals(ceil(tile * 2 + 16f), layout.groupWidth)
        assertTrue(layout.groupWidth < 2000f)
    }

    @Test
    fun `no devices lay out to nothing`() {
        val layout = layoutDeviceTiles(emptyList(), width = 800f, height = 600f, metrics = METRICS)

        assertTrue(layout.rows.isEmpty())
    }

    @Test
    fun `freshness reads in seconds, then minutes, and says when the screen is off`() {
        fun thumbnail(state: ThumbnailState) = DeviceThumbnail(image = null as ImageBitmap?, updatedAtMillis = 10_000L, state = state)

        assertEquals("Updated just now", freshness(thumbnail(ThumbnailState.Live), nowMillis = 11_000L))
        assertEquals("Updated 5 s ago", freshness(thumbnail(ThumbnailState.Live), nowMillis = 15_000L))
        assertEquals("Updated 2 min ago", freshness(thumbnail(ThumbnailState.Live), nowMillis = 130_000L))
        assertEquals("Screen off · last seen 5 s ago", freshness(thumbnail(ThumbnailState.ScreenOff), nowMillis = 15_000L))
        assertEquals("Capturing…", freshness(DeviceThumbnail(image = null, updatedAtMillis = null, state = ThumbnailState.Loading), nowMillis = 0L))
    }

    private fun assertFits(layout: DeviceTileLayout, width: Float, height: Float) {
        assertFalse(layout.scrolls)
        assertTrue(layout.groupWidth <= width, "the group is ${layout.groupWidth} wide in $width")
        assertTrue(layout.groupHeight <= height + 1f, "the group is ${layout.groupHeight} tall in $height")
    }
}
