package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * A square icon button of [JwMetrics.controlHeight]: flat until hovered, tinted while
 * [selected]. The icon slot receives the content color to draw with; [JwIcon] is the usual
 * thing to put in it.
 *
 * @param onClick what the button does.
 * @param tooltip shown on hover, and the button's accessibility name — the only label a bare
 * icon carries, so pass one. Leave the icon's own contentDescription null then, or the two merge.
 * @param enabled false greys the icon out and ignores clicks.
 * @param selected tints the button, for a toggle that is on or the item that is current.
 * @param size the button's side; the icon inside keeps [JwMetrics.iconSize].
 * @param content the icon, usually a [JwIcon].
 */
@Composable
public fun JwIconButton(
    onClick: () -> Unit,
    tooltip: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = JwMetrics.controlHeight,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val background = when {
        !enabled -> Color.Transparent
        selected -> JwTheme.colors.selection
        pressed -> JwTheme.colors.hover.copy(alpha = JwTheme.colors.hover.alpha * 2f)
        hovered -> JwTheme.colors.hover
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> JwTheme.colors.textDisabled
        selected -> MaterialTheme.colorScheme.primary
        else -> LocalContentColor.current
    }
    JwTooltip(text = tooltip, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .then(if (tooltip != null) Modifier.semantics { contentDescription = tooltip } else Modifier)
                .jwFocusRing(interactionSource, MaterialTheme.shapes.small)
                .clip(MaterialTheme.shapes.small)
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
        }
    }
}

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
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(JwMetrics.iconSize),
    )
}

/**
 * An icon at [JwMetrics.iconSize], tinted with the current content color.
 *
 * @param painter the glyph, such as a plugin's SVG icon.
 * @param contentDescription what the icon means, or null when a neighboring label already says it.
 * @param tint the color to draw with; defaults to the content color of the enclosing control.
 */
@Composable
public fun JwIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(JwMetrics.iconSize),
    )
}
