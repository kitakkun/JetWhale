package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A select-style trigger of [JwMetrics.controlHeight]: [leading] icon, the current [text], a
 * chevron, and the [menu] it drops down. The caller owns [expanded] so it can close the menu from a
 * [JwMenuItem].
 *
 * @param text the current value, or a placeholder when nothing is selected.
 * @param expanded whether the menu is open.
 * @param onExpandedChange called with the requested open state: `true` on click, `false` on dismiss.
 * @param enabled false greys the trigger out and keeps the menu closed.
 * @param leading an optional glyph before the text.
 * @param trailing optional badges between the text and the chevron: a status dot, a lock.
 * @param menu the [JwMenuItem]s shown while [expanded].
 */
@Composable
public fun JwDropdownButton(
    text: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    menu: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val colors = JwTheme.colors
    val shape = MaterialTheme.shapes.small
    val contentColor = if (enabled) scheme.onSurface else colors.textDisabled
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(JwMetrics.controlHeight)
                .clip(shape)
                .background(if (hovered && enabled) colors.hover else scheme.surfaceContainerLowest, shape)
                .border(JwMetrics.borderWidth, if (enabled) scheme.outline else colors.border.copy(alpha = 0.5f), shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.DropdownList,
                    onClick = { onExpandedChange(!expanded) },
                )
                .padding(start = JwSpacing.medium, end = JwSpacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                leading?.invoke()
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke(this)
                JwIcon(
                    imageVector = JwIcons.ChevronDown,
                    contentDescription = null,
                    tint = if (enabled) colors.textSecondary else colors.textDisabled,
                )
            }
        }
        JwDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            content = menu,
        )
    }
}

/**
 * A popup menu anchored to the composable it is placed next to, holding [JwMenuItem]s.
 *
 * @param expanded whether the menu is shown.
 * @param onDismissRequest called on Escape or a click outside the menu.
 * @param content the [JwMenuItem]s, in order.
 */
@Composable
public fun JwDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .widthIn(min = 160.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(JwMetrics.borderWidth, JwTheme.colors.border, MaterialTheme.shapes.small)
            .padding(JwSpacing.extraSmall),
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 6.dp,
        content = content,
    )
}

/**
 * One row of a [JwDropdownMenu].
 *
 * @param text the item's label.
 * @param onClick what the item does; the caller also closes the menu here.
 * @param enabled false greys the item out and ignores clicks.
 * @param selected draws a check mark in the leading slot, for menus that pick one of several values.
 * @param leading an optional glyph, shown when the item is not [selected].
 * @param trailing optional annotations at the far end: a shortcut, a secondary value.
 * @param tone [JwTone.Error] colors the item red for a destructive action; other tones read as
 * [JwTone.Neutral].
 */
@Composable
public fun JwMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    tone: JwTone = JwTone.Neutral,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val colors = JwTheme.colors
    val contentColor = when {
        !enabled -> colors.textDisabled
        tone == JwTone.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (hovered && enabled) colors.hover else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = JwSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(modifier = Modifier.size(JwMetrics.iconSize), contentAlignment = Alignment.Center) {
                when {
                    selected -> JwIcon(imageVector = JwIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    leading != null -> leading()
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke(this)
        }
    }
}
