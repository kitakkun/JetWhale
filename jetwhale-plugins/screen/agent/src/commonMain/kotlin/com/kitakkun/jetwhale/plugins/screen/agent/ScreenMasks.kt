package com.kitakkun.jetwhale.plugins.screen.agent

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Blacks this element out of every streamed frame. The pixels are covered on the device, before the
 * frame is encoded, so what is masked never leaves the app.
 */
fun Modifier.maskedInScreenStream(): Modifier = this then MaskedElement

/** A rectangle on screen, in screen pixels. */
internal data class MaskRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A rectangle in a frame's pixels, clamped to the frame. */
internal data class FrameRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * This screen rectangle in a frame drawn at [scale] of the screen, grown outwards to whole pixels so
 * that scaling never leaves a sliver of the masked content uncovered; null when nothing of it falls
 * inside the frame.
 */
internal fun MaskRect.toFrame(scale: Float, frameWidth: Int, frameHeight: Int): FrameRect? {
    val rect = FrameRect(
        left = floor(left * scale).toInt().coerceIn(0, frameWidth),
        top = floor(top * scale).toInt().coerceIn(0, frameHeight),
        right = ceil(right * scale).toInt().coerceIn(0, frameWidth),
        bottom = ceil(bottom * scale).toInt().coerceIn(0, frameHeight),
    )
    return rect.takeIf { it.right > it.left && it.bottom > it.top }
}

/** Where every masked element currently is. Written from layout, read by the capture thread. */
internal object ScreenMasks {
    private val rects = MutableStateFlow<Map<Any, MaskRect>>(emptyMap())

    fun current(): Collection<MaskRect> = rects.value.values

    fun put(owner: Any, rect: MaskRect) = rects.update { it + (owner to rect) }

    fun remove(owner: Any) = rects.update { it - owner }
}

private data object MaskedElement : ModifierNodeElement<MaskedNode>() {
    override fun create(): MaskedNode = MaskedNode()

    override fun update(node: MaskedNode) = Unit
}

private class MaskedNode :
    Modifier.Node(),
    GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val topLeft = coordinates.positionOnScreen()
        ScreenMasks.put(
            owner = this,
            rect = MaskRect(
                left = topLeft.x,
                top = topLeft.y,
                right = topLeft.x + coordinates.size.width,
                bottom = topLeft.y + coordinates.size.height,
            ),
        )
    }

    override fun onDetach() {
        ScreenMasks.remove(this)
    }
}
