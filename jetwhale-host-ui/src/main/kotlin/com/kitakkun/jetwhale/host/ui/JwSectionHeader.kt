package com.kitakkun.jetwhale.host.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The heading of a group of rows: a small secondary-colored [title], an optional [count], and
 * whatever [trailing] controls belong to the group.
 *
 * Pass [expanded] and [onToggleExpanded] to make the group collapsible; the header then shows a
 * chevron and toggles on click.
 */
@Composable
public fun JwSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    expanded: Boolean? = null,
    onToggleExpanded: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val collapsible = expanded != null && onToggleExpanded != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(JwMetrics.sectionHeaderHeight)
            .then(if (collapsible) Modifier.clickable(onClick = onToggleExpanded) else Modifier)
            .padding(horizontal = JwSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.xs),
    ) {
        if (expanded != null) {
            val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "section-chevron")
            JwIcon(
                imageVector = JwIcons.ChevronRight,
                contentDescription = null,
                tint = JwTheme.colors.textSecondary,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(rotation),
            )
        }
        // Title and count share the remaining width, the count hugging the title; anything
        // trailing keeps its own size at the far end.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.xs),
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
