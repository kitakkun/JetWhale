package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

/**
 * The device's screen, fitted to the space it is given. Only this element redraws for a new frame:
 * it reads [MirrorSurface.frameCounter] in its draw phase, so a frame costs a draw and never a
 * recomposition of what surrounds it. When [interactive], a click taps the device and a drag
 * swipes it.
 */
@Composable
internal fun MirrorVideo(
    surface: MirrorSurface,
    deviceId: String,
    interactive: Boolean,
    onTap: (x: Int, y: Int) -> Unit,
    onSwipe: (fromX: Int, fromY: Int, toX: Int, toY: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Where the last frame was drawn, for mapping the pointer to device pixels; set by drawing.
    val drawn = remember { DrawnFrame() }
    val input = if (interactive) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val fitted = drawn.fitted ?: return@awaitEachGesture
                val device = surface.deviceSize ?: drawn.frameSize
                val start = fitted.toDevicePixel(down.position, device) ?: return@awaitEachGesture
                var last = down.position
                do {
                    val event = awaitPointerEvent()
                    event.changes.firstOrNull()?.let { last = it.position }
                } while (event.changes.any(PointerInputChange::pressed))
                val end = fitted.toDevicePixel(last, device) ?: start
                if ((last - down.position).getDistance() < viewConfiguration.touchSlop) {
                    onTap(start.x, start.y)
                } else {
                    onSwipe(start.x, start.y, end.x, end.y)
                }
            }
        }
    } else {
        Modifier
    }
    Canvas(modifier.then(input).onSizeChanged { surface.viewSize = it }) {
        surface.frameCounter
        val started = System.nanoTime()
        // Until the surface has switched to the selected device, the previous one's stream is still
        // shutting down: its frames must not appear under this device's name.
        val drawFrame: ((Bitmap) -> Unit) -> Unit = if (surface.deviceId == deviceId) surface::drawFrame else { draw -> surface.drawKeptFrame(deviceId, draw) }
        drawFrame { bitmap ->
            val fitted = FittedFrame(bitmap.width, bitmap.height, size.width, size.height)
            drawn.fitted = fitted
            drawn.frameSize = IntSize(bitmap.width, bitmap.height)
            val bounds = fitted.bounds
            Image.makeFromBitmap(bitmap).use { image ->
                drawIntoCanvas { canvas ->
                    canvas.skiaCanvas.drawImageRect(
                        image,
                        Rect.makeWH(bitmap.width.toFloat(), bitmap.height.toFloat()),
                        Rect.makeLTRB(l = bounds.left, t = bounds.top, r = bounds.right, b = bounds.bottom),
                        // Shrinking a phone screen by half or more with plain bilinear sampling skips
                        // source pixels and leaves text jagged; mipmaps average them. Enlarging has
                        // nothing to average, so bilinear does there.
                        if (bounds.width < bitmap.width) SHRINKING else SamplingMode.LINEAR,
                        null,
                        true,
                    )
                }
            }
            surface.recordDraw(System.nanoTime() - started)
        }
    }
}

private class DrawnFrame {
    var fitted: FittedFrame? = null
    var frameSize: IntSize = IntSize.Zero
}

private val SHRINKING = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
