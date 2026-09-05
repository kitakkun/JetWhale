package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow

/**
 * A selectable row for sidebars and lists: [leading] icon, a single-line [text], and [trailing]
 * badges or menus. A [supportingText] adds a second, secondary-colored line and makes the row taller;
 * without one the row is [JwMetrics.controlHeight]. The row tints on hover and on [selected],
 * and fades when not [enabled].
 *
 * @param text the row's label, ellipsized to one line.
 * @param selected whether this is the current item.
 * @param onClick what selecting the row does.
 * @param enabled false fades the row and ignores clicks.
 * @param supportingText a second line under [text].
 * @param leading an optional glyph before the text.
 * @param trailing badges or an overflow menu at the far end.
 */
@Composable
public fun JwListItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = when {
        selected -> JwTheme.colors.selection
        hovered && enabled -> JwTheme.colors.hover
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> JwTheme.colors.textDisabled
        selected -> JwTheme.colors.onSelection
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (supportingText == null) {
                    Modifier.height(JwMetrics.controlHeight)
                } else {
                    Modifier.padding(vertical = JwSpacing.extraSmall)
                },
            )
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = JwSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) JwTheme.colors.textSecondary else JwTheme.colors.textDisabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}
