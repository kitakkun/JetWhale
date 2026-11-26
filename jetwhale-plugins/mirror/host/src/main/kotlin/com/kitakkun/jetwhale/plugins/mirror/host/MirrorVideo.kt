package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
import java.awt.Cursor

/**
 * The device's screen, fitted to the space it is given. Only this element redraws for a new frame:
 * it reads [MirrorSurface.frameCounter] in its draw phase, so a frame costs a draw and never a
 * recomposition of what surrounds it. When [interactive], a click on a live frame taps the device
 * and a drag swipes it; while [onLookAround] is set, a drag instead reports each move as it happens,
 * in widths of the screen across and down, and a click still taps.
 */
@Composable
internal fun MirrorVideo(
    surface: MirrorSurface,
    deviceId: String,
    interactive: Boolean,
    onTap: (x: Int, y: Int) -> Unit,
    onSwipe: (fromX: Int, fromY: Int, toX: Int, toY: Int) -> Unit,
    onLookAround: ((dragXWidths: Float, dragYWidths: Float) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val drawn = remember { DrawnFrame() }
    val live = surface.deviceId == deviceId && !surface.showingKeptFrame
    val currentOnLookAround by rememberUpdatedState(onLookAround)
    val input = if (interactive && live) {
        Modifier
            .pointerInput(Unit) { awaitEachGesture { followGesture(drawn, surface, onTap, onSwipe, currentOnLookAround) } }
            .then(if (onLookAround != null) Modifier.pointerHoverIcon(LOOK_AROUND_POINTER_ICON) else Modifier)
    } else {
        Modifier
    }
    // Its own layer, so a new frame re-records only the video: Compose re-records the whole
    // nearest layer when state read in a draw changes.
    Canvas(modifier.then(input).onSizeChanged { surface.viewSize = it }.graphicsLayer()) {
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

/**
 * Follows one press on a live frame to its release: a click taps, and a drag swipes once it ends, or,
 * with [onLookAround], reports each move as it happens instead.
 */
private suspend fun AwaitPointerEventScope.followGesture(
    drawn: DrawnFrame,
    surface: MirrorSurface,
    onTap: (x: Int, y: Int) -> Unit,
    onSwipe: (fromX: Int, fromY: Int, toX: Int, toY: Int) -> Unit,
    onLookAround: ((dragXWidths: Float, dragYWidths: Float) -> Unit)?,
) {
    val down = awaitFirstDown().position
    val fitted = drawn.fitted ?: return
    val device = surface.deviceSize ?: drawn.frameSize
    val start = fitted.toDevicePixel(down, device) ?: return
    if (onLookAround != null) {
        val last = lookAroundUntilRelease(down, fitted.bounds.width, onLookAround)
        if ((last - down).getDistance() < viewConfiguration.touchSlop) onTap(start.x, start.y)
    } else {
        val last = awaitReleasePosition(down)
        val end = fitted.toDevicePixel(last, device) ?: start
        if ((last - down).getDistance() < viewConfiguration.touchSlop) onTap(start.x, start.y) else onSwipe(start.x, start.y, end.x, end.y)
    }
}

/** Waits for the press that went down at [down] to end, and returns where the pointer was last. */
private suspend fun AwaitPointerEventScope.awaitReleasePosition(down: Offset): Offset {
    var last = down
    do {
        val event = awaitPointerEvent()
        event.changes.firstOrNull()?.let { last = it.position }
    } while (event.changes.any(PointerInputChange::pressed))
    return last
}

/**
 * Reports each move of the press that went down at [down], once it has left the touch slop, in widths
 * of the [frameWidth] across and down, until it ends; returns where the pointer was last.
 */
private suspend fun AwaitPointerEventScope.lookAroundUntilRelease(down: Offset, frameWidth: Float, onLookAround: (dragXWidths: Float, dragYWidths: Float) -> Unit): Offset {
    var last = down
    var reportedTo: Offset? = null
    do {
        val event = awaitPointerEvent()
        event.changes.firstOrNull()?.let { last = it.position }
        val from = reportedTo ?: down.takeIf { (last - it).getDistance() >= viewConfiguration.touchSlop }
        if (from != null && last != from) {
            onLookAround((last.x - from.x) / frameWidth, (last.y - from.y) / frameWidth)
            reportedTo = last
        }
    } while (event.changes.any(PointerInputChange::pressed))
    return last
}

private class DrawnFrame {
    var fitted: FittedFrame? = null
    var frameSize: IntSize = IntSize.Zero
}

private val SHRINKING = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

private val LOOK_AROUND_POINTER_ICON = PointerIcon(Cursor(Cursor.MOVE_CURSOR))
