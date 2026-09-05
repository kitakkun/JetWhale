package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow

/**
 * A bordered group of related content — the settings of one topic, the details of one item. The
 * optional [title] sits in a header strip with its own hairline, and [content] is laid out as a
 * column inside [contentPadding]. Pass `PaddingValues(0.dp)` to fill the panel edge to edge with
 * rows or a list of your own.
 *
 * @param title the header strip's text; omit it for an untitled box.
 * @param contentPadding the space between the border and [content].
 * @param headerActions controls at the far end of the header strip.
 * @param content the body, laid out as a column with [JwSpacing.medium] between children.
 */
@Composable
public fun JwPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(JwSpacing.large),
    headerActions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape)
            .border(JwMetrics.borderWidth, JwTheme.colors.border, shape),
    ) {
        if (title != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = JwSpacing.large, vertical = JwSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                headerActions?.invoke()
            }
            JwHorizontalDivider()
        }
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
            content = content,
        )
    }
}
