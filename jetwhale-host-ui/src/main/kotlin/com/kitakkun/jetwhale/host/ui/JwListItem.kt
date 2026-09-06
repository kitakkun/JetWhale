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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A selectable row for sidebars and lists: [leadingContent], a single-line [text], and
 * [trailingContent] badges or menus. Named after Material's `ListItem`; [supportingText] is the
 * string form of its `supportingContent`. A [supportingText] adds a second, secondary-colored line and makes the row taller;
 * without one the row is [JwMetrics.controlHeight]. The row tints on hover and on [selected],
 * and fades when not [enabled].
 *
 * @param text the row's label, ellipsized to one line.
 * @param selected whether this is the current item.
 * @param onClick what selecting the row does.
 * @param enabled false fades the row and ignores clicks.
 * @param muted draws the row in the secondary text color while keeping it interactive — an item
 * that is present but not current, say. Distinct from [enabled], which removes the interaction.
 * @param supportingText a second line under [text].
 * @param leadingContent content before the text: usually a [JwIcon], but an app icon or an avatar fits too.
 * @param trailingContent badges or an overflow menu at the far end.
 */
@Composable
public fun JwListItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    muted: Boolean = false,
    supportingText: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    JwListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        muted = muted,
    ) {
        leadingContent?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            JwText(
                text = text,
                style = JwTheme.textStyles.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                JwText(
                    text = supportingText,
                    style = JwTheme.textStyles.bodySmall,
                    color = if (enabled) JwTheme.colors.textSecondary else JwTheme.colors.textDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingContent?.invoke(this)
    }
}

/**
 * A selectable row whose content is laid out by the caller — several columns, a monospace name
 * over a secondary line, a badge that is not at the end. It keeps everything the text overload
 * has: hover and selection tints, the focus ring, [muted] and [enabled], and `selected` in
 * semantics. The content is a row with [JwSpacing.medium] between children, drawn in the row's
 * content color. The row is [JwMetrics.controlHeight] tall unless the content is taller — a
 * default-sized [JwIconButton] is, so give one inside a row [JwIconButtonDefaults.inlineSize].
 *
 * @param selected whether this is the current item.
 * @param onClick what selecting the row does.
 * @param enabled false fades the row and ignores clicks.
 * @param muted draws the row in the secondary text color while keeping it interactive.
 * @param content the row content.
 */
@Composable
public fun JwListItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    muted: Boolean = false,
    content: @Composable RowScope.() -> Unit,
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
        muted -> JwTheme.colors.textSecondary
        selected -> JwTheme.colors.onSelection
        else -> JwTheme.colors.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = JwMetrics.controlHeight)
            .jwFocusRing(interactionSource, JwShapes.small)
            .clip(JwShapes.small)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = JwSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        CompositionLocalProvider(LocalJwContentColor provides contentColor, content = { content() })
    }
}
