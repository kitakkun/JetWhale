package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * A device frame of [frameWidth] by [frameHeight] pixels scaled to fit a view of [viewWidth] by
 * [viewHeight], centered with its aspect ratio kept; the space left over is letterboxing.
 */
internal class FittedFrame(
    frameWidth: Int,
    frameHeight: Int,
    viewWidth: Float,
    viewHeight: Float,
) {
    private val scale = minOf(viewWidth / frameWidth, viewHeight / frameHeight)

    /** Where the frame is drawn in the view. */
    val bounds: Rect = Rect(
        offset = Offset((viewWidth - frameWidth * scale) / 2, (viewHeight - frameHeight * scale) / 2),
        size = Size(frameWidth * scale, frameHeight * scale),
    )

    /**
     * The pixel of a [device] screen under [point] of the view, or null when [point] is on the
     * letterboxing. The frame may be smaller than the screen it shows, so the point is taken as a
     * share of the frame and scaled to the screen.
     */
    fun toDevicePixel(point: Offset, device: IntSize): IntOffset? {
        if (!bounds.contains(point)) return null
        val x = ((point.x - bounds.left) / bounds.width * device.width).toInt().coerceIn(0, device.width - 1)
        val y = ((point.y - bounds.top) / bounds.height * device.height).toInt().coerceIn(0, device.height - 1)
        return IntOffset(x, y)
    }
}

/**
 * The size to decode a [source] screen at for a [view] of the given size: the screen fitted to the
 * view, or null to keep it whole when the view is as large or not laid out yet. Even sides, since
 * the decoder's 4:2:0 frames cannot have odd ones.
 */
internal fun decodingSize(source: IntSize, view: IntSize): IntSize? {
    if (view.width <= 0 || view.height <= 0) return null
    val scale = minOf(view.width.toFloat() / source.width, view.height.toFloat() / source.height)
    if (scale >= 1f) return null
    return IntSize(evenAtLeastTwo(source.width * scale), evenAtLeastTwo(source.height * scale))
}

private fun evenAtLeastTwo(value: Float): Int = maxOf(2, value.toInt() and 1.inv())
