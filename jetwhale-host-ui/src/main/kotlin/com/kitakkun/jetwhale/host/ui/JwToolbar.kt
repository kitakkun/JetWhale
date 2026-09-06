package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * A [JwMetrics.toolbarHeight] bar across the top of a pane: an optional [title] on the left,
 * [actions] (usually [JwIconButton]s) on the right, and a hairline underneath.
 *
 * @param title the pane's name, ellipsized to one line; null for a bar of controls only.
 * @param navigationIcon controls before the title: a back button, a picker. Named after Material's
 * `TopAppBar`, though any row content fits.
 * @param actions controls at the far end, usually [JwIconButton]s or text [JwButton]s.
 */
@Composable
public fun JwToolbar(
    title: String? = null,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(JwMetrics.toolbarHeight)
                .background(JwTheme.colors.toolbarBackground)
                .padding(horizontal = JwSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
        ) {
            navigationIcon?.invoke(this)
            if (title != null) {
                JwText(
                    text = title,
                    style = JwTheme.textStyles.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = JwSpacing.extraSmall),
                )
            } else {
                Row(modifier = Modifier.weight(1f)) {}
            }
            actions?.invoke(this)
        }
        JwHorizontalDivider()
    }
}
