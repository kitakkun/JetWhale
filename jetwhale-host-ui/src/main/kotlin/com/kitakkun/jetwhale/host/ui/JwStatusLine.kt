package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwStatusLine]. */
public object JwStatusLineDefaults {
    /** Height of the line. */
    public val height: Dp = 24.dp
}

/**
 * A one-line strip of secondary text at the top or bottom of a pane: "4 / 4 requests",
 * "3 roots · 120 nodes · 12 ms", the last action's outcome. Quieter than a [JwBanner]: it states,
 * it does not ask. [trailingContent] holds anything that belongs at the far end — a count, a
 * spinner.
 *
 * @param text the message, kept to one line and ellipsized.
 * @param tone [JwTone.Neutral] draws secondary text; [JwTone.Error] and [JwTone.Warning] color it
 * so a failure is not missed in a line of statistics.
 * @param trailingContent content at the far end of the line.
 */
@Composable
public fun JwStatusLine(
    text: String,
    modifier: Modifier = Modifier,
    tone: JwTone = JwTone.Neutral,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(JwStatusLineDefaults.height)
            .background(JwTheme.colors.toolbarBackground)
            .padding(horizontal = JwSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        JwText(
            text = text,
            style = JwTheme.textStyles.bodySmall,
            color = if (tone == JwTone.Neutral) JwTheme.colors.textSecondary else tone.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailingContent?.invoke(this)
    }
}
