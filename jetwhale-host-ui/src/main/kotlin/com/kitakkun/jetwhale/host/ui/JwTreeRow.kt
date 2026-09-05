package com.kitakkun.jetwhale.host.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwTreeRow]. */
public object JwTreeRowDefaults {
    /** Height of a row. Tighter than [JwMetrics.controlHeight]: a tree is read as a block. */
    public val height: Dp = 24.dp

    /** Horizontal indent per level. */
    public val indent: Dp = 14.dp
}

/** Side of the chevron glyph. */
private val ChevronSize = 14.dp

/**
 * One line of a flattened tree: indented by [depth], with a chevron that toggles the subtree when
 * the node is [expandable], then the [text] and any [trailing] annotations. Selection and hover
 * tint the row like [JwListItem] does; the chevron's own click never selects, so a tree can be
 * browsed without moving the selection.
 *
 * Nodes at the same depth line up whether or not they have children: a leaf keeps an empty slot
 * where the chevron would be.
 *
 * @param text the node's label, ellipsized to one line.
 * @param depth how many levels deep the node is; 0 for a root.
 * @param expandable whether the node has children to show.
 * @param expanded whether its children are showing; ignored when not [expandable].
 * @param selected whether this is the current node.
 * @param onClick what selecting the row does.
 * @param onToggleExpanded called when the chevron is clicked.
 * @param enabled false fades the row and ignores clicks, including the chevron's.
 * @param muted draws the row in the secondary text color while keeping it fully interactive — for
 * a node that exists but is not currently shown, say. Distinct from [enabled], which removes the
 * interaction as well.
 * @param contentPadding the space at the row's ends, before the indent is added; match it to the
 * gutter of neighboring rows.
 * @param trailing annotations at the far end: a tag, an id.
 */
@Composable
public fun JwTreeRow(
    text: String,
    depth: Int,
    expandable: Boolean,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    muted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(start = JwSpacing.extraSmall, end = JwSpacing.medium),
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val chevronInteractionSource = remember { MutableInteractionSource() }
    val chevronHovered by chevronInteractionSource.collectIsHoveredAsState()
    val colors = JwTheme.colors
    val background = when {
        selected -> colors.selection
        hovered && enabled -> colors.hover
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> colors.textDisabled
        muted -> colors.textSecondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "tree-chevron")
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(JwTreeRowDefaults.height)
            .jwFocusRing(interactionSource, MaterialTheme.shapes.small)
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + JwTreeRowDefaults.indent * depth,
                end = contentPadding.calculateEndPadding(layoutDirection),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
    ) {
        Box(
            modifier = Modifier
                .size(JwMetrics.iconSize)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(if (expandable && chevronHovered && enabled) colors.hover else Color.Transparent)
                .then(
                    if (expandable) {
                        Modifier
                            .clickable(
                                interactionSource = chevronInteractionSource,
                                indication = null,
                                enabled = enabled,
                                role = Role.Button,
                                onClick = onToggleExpanded,
                            )
                            // Language-neutral: the matching action tells screen readers and the
                            // host's MCP tools what the chevron does without a label.
                            .semantics {
                                if (expanded) {
                                    collapse {
                                        onToggleExpanded()
                                        true
                                    }
                                } else {
                                    expand {
                                        onToggleExpanded()
                                        true
                                    }
                                }
                            }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (expandable) {
                JwIcon(
                    imageVector = JwIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(ChevronSize)
                        .rotate(rotation),
                )
            }
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            trailing?.invoke(this)
        }
    }
}
