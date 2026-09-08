package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwIconButton]. */
public object JwIconButtonDefaults {
    /** The default side: one compact control. */
    public val size: Dp = JwMetrics.controlHeight

    /** The side for a button inside a row or a field, where the default would stretch the row. */
    public val inlineSize: Dp = 20.dp
}

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
 * @param size the button's side; the icon inside keeps [JwMetrics.iconSize]. See [JwIconButtonDefaults].
 * @param content the icon, usually a [JwIcon].
 */
@Composable
public fun JwIconButton(
    onClick: () -> Unit,
    tooltip: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = JwIconButtonDefaults.size,
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
        selected -> JwTheme.colors.accent
        else -> LocalJwContentColor.current
    }
    JwTooltip(text = tooltip, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .then(if (tooltip != null) Modifier.semantics { contentDescription = tooltip } else Modifier)
                .jwFocusRing(interactionSource, JwShapes.small)
                .clip(JwShapes.small)
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
            CompositionLocalProvider(LocalJwContentColor provides contentColor, content = content)
        }
    }
}
