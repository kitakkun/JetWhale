package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/** Space between a control's edge and its focus ring. */
private val FocusRingGap = 1.dp

/** Where a focus ring sits relative to the control's own bounds. */
public enum class JwFocusRingStyle {
    /**
     * Just inside the control's bounds, so no ancestor can clip the ring away. Right for a row, a
     * button with padding, or any surface with room to spare inside its edge.
     */
    Inset,

    /**
     * Just outside the control's bounds. Right for a small control whose whole surface is painted
     * — a switch track, a checkbox, an icon button, a filled tag — where a ring drawn inside would
     * land on the control itself. It needs an ancestor that does not clip, and space around the
     * control for the ring to occupy.
     */
    Outset,
}

/**
 * Draws the accent focus ring every Jw control shows while it holds focus. On desktop a click
 * moves focus too, so the ring also marks the control last clicked until focus moves on — the
 * convention of desktop IDEs. The controls draw no ripple, so this is the only sign of focus a
 * keyboard user gets; apply it to a custom control built on the same
 * [InteractionSource] as its `clickable`.
 *
 * Place it before any `clip` in the modifier chain, or the clip cuts it off.
 *
 * @param interactionSource the control's interaction source; the ring follows its focus state.
 * @param shape the control's shape, so the ring hugs its corners.
 * @param style [JwFocusRingStyle.Inset] by default, because a shared component cannot know what
 * its container does: a ring drawn outside survives only where no ancestor clips, and a row inside
 * a scrolling panel loses it at the panel's edges. Pass [JwFocusRingStyle.Outset] for a small
 * control whose painted surface leaves no room for a ring inside it.
 */
@Composable
public fun Modifier.jwFocusRing(
    interactionSource: InteractionSource,
    shape: Shape,
    style: JwFocusRingStyle = JwFocusRingStyle.Inset,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    if (!focused) return this
    val color = JwTheme.colors.accent
    return drawWithContent {
        drawContent()
        val stroke = JwMetrics.focusStrokeWidth.toPx()
        val gap = FocusRingGap.toPx()
        // Never past the center of a control smaller than the ring's own offset, which would ask
        // for an outline of negative size.
        val offset = (stroke / 2f + gap).coerceAtMost(minOf(size.width, size.height) / 2f)
        val ringSize = when (style) {
            JwFocusRingStyle.Inset -> Size(size.width - offset * 2f, size.height - offset * 2f)
            JwFocusRingStyle.Outset -> Size(size.width + offset * 2f, size.height + offset * 2f)
        }
        val translation = when (style) {
            JwFocusRingStyle.Inset -> offset
            JwFocusRingStyle.Outset -> -offset
        }
        val outline = shape.createOutline(ringSize, layoutDirection, this)
        translate(translation, translation) {
            drawOutline(outline = outline, color = color, style = Stroke(width = stroke))
        }
    }
}
