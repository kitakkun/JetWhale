package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * A [JwMetrics.toolbarHeight] bar across the top of a pane: an optional [title] on the left,
 * [actions] (usually [JwIconButton]s) on the right, and a hairline underneath.
 *
 * @param title the pane's name, ellipsized to one line.
 * @param leading controls before the title: a back button, a picker.
 * @param actions controls at the far end, usually [JwIconButton]s or text [JwButton]s.
 */
@Composable
public fun JwToolbar(
    modifier: Modifier = Modifier,
    title: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(JwMetrics.toolbarHeight)
                .background(JwTheme.colors.toolbarBackground)
                .padding(horizontal = JwSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.xs),
        ) {
            leading?.invoke(this)
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = JwSpacing.xs),
                )
            } else {
                Row(modifier = Modifier.weight(1f)) {}
            }
            actions?.invoke(this)
        }
        JwHorizontalDivider()
    }
}
