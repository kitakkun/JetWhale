package com.kitakkun.jetwhale.host.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwSectionHeader]. */
public object JwSectionHeaderDefaults {
    /** Height of the header row. */
    public val height: Dp = 24.dp
}

/** Side of the chevron glyph. */
private val ChevronSize = 14.dp

/**
 * The heading of a group of rows: a small secondary-colored [title], an optional [count], and
 * whatever [trailing] controls belong to the group.
 *
 * Pass [expanded] and [onToggleExpanded] to make the group collapsible; the header then shows a
 * chevron and toggles on click.
 *
 * @param title the group's name.
 * @param count how many rows the group holds, shown after the title.
 * @param expanded whether the group is open; null for a group that does not collapse.
 * @param onToggleExpanded called when the header is clicked; required for the click to do anything.
 * @param contentPadding the space between the header's edge and its content; match it to the
 * gutter of the rows below.
 * @param trailing controls at the far end: an add button, a filter.
 */
@Composable
public fun JwSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    expanded: Boolean? = null,
    onToggleExpanded: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = JwSpacing.medium),
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val collapsible = expanded != null && onToggleExpanded != null
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(JwSectionHeaderDefaults.height)
            .jwFocusRing(interactionSource, MaterialTheme.shapes.small)
            .background(if (hovered && collapsible) JwTheme.colors.hover else Color.Transparent)
            .then(
                if (collapsible) {
                    Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onToggleExpanded,
                        )
                        // Language-neutral state for screen readers and the host's MCP tools: the
                        // matching action is offered, so "expand" and "collapse" need no label.
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
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
    ) {
        if (expanded != null) {
            val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "section-chevron")
            JwIcon(
                imageVector = JwIcons.ChevronRight,
                contentDescription = null,
                tint = JwTheme.colors.textSecondary,
                modifier = Modifier
                    .size(ChevronSize)
                    .rotate(rotation),
            )
        }
        // Title and count share the remaining width, the count hugging the title; anything
        // trailing keeps its own size at the far end.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = JwTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = JwTheme.colors.textDisabled,
                )
            }
        }
        trailing?.invoke(this)
    }
}
