package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
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

/**
 * Draws the accent focus ring every Jw control shows while it holds focus. On desktop a click
 * moves focus too, so the ring also marks the control last clicked until focus moves on — the
 * convention of desktop IDEs. The controls suppress the Material ripple, so this is the only sign
 * of focus a keyboard user gets; apply it to a custom control built on the same
 * [InteractionSource] as its `clickable`.
 *
 * The ring sits 2.5dp outside the control's bounds, so it stays visible on a control filled with
 * the accent color itself. Place it before any `clip` in the modifier chain, or the clip cuts it
 * off; a parent that clips, such as [JwPanel], trims the ring of a row flush with its edge.
 *
 * @param interactionSource the control's interaction source; the ring follows its focus state.
 * @param shape the control's shape, so the ring hugs its corners.
 */
@Composable
public fun Modifier.jwFocusRing(interactionSource: InteractionSource, shape: Shape): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    if (!focused) return this
    val color = MaterialTheme.colorScheme.primary
    return drawWithContent {
        drawContent()
        val stroke = 1.5f.dp.toPx()
        val gap = 1.dp.toPx()
        val inset = stroke / 2f + gap
        val outline = shape.createOutline(Size(size.width + inset * 2f, size.height + inset * 2f), layoutDirection, this)
        translate(-inset, -inset) {
            drawOutline(outline = outline, color = color, style = Stroke(width = stroke))
        }
    }
}
