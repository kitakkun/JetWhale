package com.kitakkun.jetwhale.host.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Horizontal indent per tree level. */
private val TreeIndent = 14.dp

/**
 * One line of a flattened tree: indented by [depth], with a chevron that toggles the subtree when
 * the node is [expandable], then the [text] and any [trailing] annotations. Selection and hover
 * follow [JwListItem]; the chevron's own click never selects, so a tree can be browsed without
 * moving the selection.
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
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val colors = JwTheme.colors
    val background = when {
        selected -> colors.selection
        hovered && enabled -> colors.hover
        else -> Color.Transparent
    }
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else colors.textDisabled
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "tree-chevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(start = JwSpacing.extraSmall + TreeIndent * depth, end = JwSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .then(if (expandable) Modifier.clickable(enabled = enabled, onClick = onToggleExpanded) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (expandable) {
                JwIcon(
                    imageVector = JwIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(14.dp)
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
