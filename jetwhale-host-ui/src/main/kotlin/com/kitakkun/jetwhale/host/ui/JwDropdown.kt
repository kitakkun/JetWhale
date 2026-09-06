package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** Sizes of a [JwDropdownMenu] and its [JwMenuItem]s. */
public object JwMenuDefaults {
    /** Height of one [JwMenuItem]. */
    public val itemHeight: Dp = 26.dp

    /** The narrowest a menu is laid out; a short list of short items still reads as a menu. */
    public val minWidth: Dp = 160.dp
}

/** Shadow under an open [JwDropdownMenu]. */
private val MenuShadowElevation = 6.dp

/**
 * A select-style trigger of [JwMetrics.controlHeight]: [leading] icon, the current [text], a
 * chevron, and the [menu] it drops down. The caller owns [expanded] so it can close the menu from a
 * [JwMenuItem].
 *
 * @param text the current value, or a placeholder when nothing is selected.
 * @param expanded whether the menu is open.
 * @param onExpandedChange called with the requested open state: `true` on click, `false` on dismiss.
 * @param enabled false greys the trigger out and keeps the menu closed.
 * @param leadingIcon content before the text: usually a [JwIcon], but any small composable fits.
 * @param trailingIcon optional badges between the text and the chevron: a status dot, a lock.
 * @param menu the [JwMenuItem]s shown while [expanded].
 */
@Composable
public fun JwDropdownButton(
    text: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    menu: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val scheme = JwTheme.colors
    val colors = JwTheme.colors
    val shape = JwShapes.small
    val contentColor = if (enabled) scheme.onSurface else colors.textDisabled
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(JwMetrics.controlHeight)
                .jwFocusRing(interactionSource, shape)
                .clip(shape)
                .background(if (hovered && enabled) colors.hover else scheme.panelBackground, shape)
                .border(JwMetrics.borderWidth, if (enabled) scheme.controlBorder else colors.border.copy(alpha = 0.5f), shape)
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
            CompositionLocalProvider(LocalJwContentColor provides contentColor) {
                leadingIcon?.invoke()
                JwText(
                    text = text,
                    style = JwTheme.textStyles.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                trailingIcon?.invoke(this)
                JwIcon(
                    imageVector = JwIcons.ChevronDown,
                    contentDescription = null,
                    tint = if (enabled) colors.textSecondary else colors.textDisabled,
                )
            }
        }
        JwDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { onExpandedChange(false) },
            content = menu,
        )
    }
}

/** Sizes of a [JwDropdownMenu]. */
public object JwDropdownMenuDefaults {
    /** The tallest a menu grows before its items scroll. */
    public val maxHeight: Dp = 320.dp
}

/**
 * A popup menu holding [JwMenuItem]s. Place it inside the same `Box` as the control that opens it:
 * it anchors to that parent and opens below it (above, when the window has no room), takes focus
 * so Escape and a click outside dismiss it, and scrolls past [JwDropdownMenuDefaults.maxHeight].
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
    if (!expanded) return
    Popup(
        popupPositionProvider = rememberJwPopupPositionProvider(JwPopupAnchor.BelowStart),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = modifier
                // Sized by the widest item, not by the window: the items fill the menu's width, so
                // the menu must not take its width from them in turn.
                .width(IntrinsicSize.Max)
                .widthIn(min = JwMenuDefaults.minWidth)
                .heightIn(max = JwDropdownMenuDefaults.maxHeight)
                .shadow(MenuShadowElevation, JwShapes.medium)
                .background(JwTheme.colors.elevatedBackground, JwShapes.medium)
                .border(JwMetrics.borderWidth, JwTheme.colors.border, JwShapes.medium)
                .padding(JwSpacing.extraSmall)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

/**
 * One row of a [JwDropdownMenu], [JwMenuDefaults.itemHeight] tall.
 *
 * @param text the item's label.
 * @param onClick what the item does; the caller also closes the menu here.
 * @param leadingIcon content for the leading slot, shown when the item is not [selected]: usually a
 * [JwIcon], but an app icon or an avatar fits too.
 * @param trailingIcon optional annotations at the far end: a shortcut, a secondary value.
 * @param enabled false greys the item out and ignores clicks.
 * @param selected draws a check mark in the leading slot, for menus that pick one of several values.
 * @param tone [JwTone.Error] colors the item red for a destructive action; other tones read as
 * [JwTone.Neutral].
 */
@Composable
public fun JwMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    tone: JwTone = JwTone.Neutral,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val colors = JwTheme.colors
    val contentColor = when {
        !enabled -> colors.textDisabled
        tone == JwTone.Error -> JwTheme.colors.error
        else -> JwTheme.colors.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(JwMenuDefaults.itemHeight)
            .jwFocusRing(interactionSource, JwShapes.extraSmall)
            .clip(JwShapes.extraSmall)
            .background(if (hovered && enabled) colors.hover else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = JwSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        CompositionLocalProvider(LocalJwContentColor provides contentColor) {
            Box(modifier = Modifier.size(JwMetrics.iconSize), contentAlignment = Alignment.Center) {
                when {
                    selected -> JwIcon(imageVector = JwIcons.Check, contentDescription = null, tint = JwTheme.colors.accent)
                    leadingIcon != null -> leadingIcon()
                }
            }
            JwText(
                text = text,
                style = JwTheme.textStyles.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailingIcon?.invoke(this)
        }
    }
}
