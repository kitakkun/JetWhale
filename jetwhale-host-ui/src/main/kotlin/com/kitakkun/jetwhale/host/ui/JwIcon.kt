package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/**
 * An icon at [JwMetrics.iconSize], tinted with the current content color.
 *
 * @param imageVector the glyph.
 * @param contentDescription what the icon means, or null when a neighboring label already says it.
 * @param tint the color to draw with; defaults to the content color of the enclosing control.
 */
@Composable
public fun JwIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalJwContentColor.current,
) {
    JwIcon(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/**
 * An icon at [JwMetrics.iconSize], tinted with the current content color.
 *
 * @param painter the glyph, such as a plugin's SVG icon.
 * @param contentDescription what the icon means, or null when a neighboring label already says it.
 * @param tint the color to draw with; defaults to the content color of the enclosing control.
 * [Color.Unspecified] draws the painter in its own colors.
 */
@Composable
public fun JwIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalJwContentColor.current,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(JwMetrics.iconSize),
        colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
    )
}
