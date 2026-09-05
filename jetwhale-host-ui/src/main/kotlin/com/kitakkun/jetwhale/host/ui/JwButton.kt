package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role

/** How prominent a [JwButton] is. One primary button per view is plenty. */
public enum class JwButtonStyle {
    /** Filled with the accent color: the one action the view is for. */
    Primary,

    /** Outlined: the ordinary actions. */
    Secondary,

    /** Text only: actions inside banners and rows, where a box would compete with the content. */
    Text,
}

/**
 * A compact text button of [JwMetrics.controlHeight].
 *
 * @param text the label; keep it to a verb phrase.
 * @param onClick what the button does.
 * @param style how prominent the button is; see [JwButtonStyle].
 * @param tone [JwTone.Accent] is the ordinary button. [JwTone.Error] marks a destructive
 * action — a delete, a clear — in every style: filled red as Primary, red text otherwise.
 * @param enabled false greys the button out and ignores clicks.
 * @param leadingIcon an optional glyph before the label, drawn in the button's content color.
 */
@Composable
public fun JwButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: JwButtonStyle = JwButtonStyle.Secondary,
    tone: JwTone = JwTone.Accent,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val colors = JwTheme.colors
    val accent = tone.color
    val onAccent = tone.onColor
    val background = when {
        !enabled -> if (style == JwButtonStyle.Primary) scheme.onSurface.copy(alpha = 0.12f) else Color.Transparent
        style == JwButtonStyle.Primary -> if (hovered) accent.copy(alpha = 0.88f) else accent
        hovered -> colors.hover
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> colors.textDisabled
        style == JwButtonStyle.Primary -> onAccent
        style == JwButtonStyle.Text -> accent
        tone == JwTone.Accent -> scheme.onSurface
        else -> accent
    }
    val borderColor = when {
        style != JwButtonStyle.Secondary -> Color.Transparent
        !enabled -> colors.border.copy(alpha = 0.5f)
        tone == JwTone.Accent -> scheme.outline
        else -> accent.copy(alpha = 0.6f)
    }
    Row(
        modifier = modifier
            .height(JwMetrics.controlHeight)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .border(JwMetrics.borderWidth, borderColor, MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = JwSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            leadingIcon?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}
