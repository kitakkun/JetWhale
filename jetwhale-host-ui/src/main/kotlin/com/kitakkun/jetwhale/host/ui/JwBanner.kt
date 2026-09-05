package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A one-line strip across the top of a pane that reports something the user should know but not
 * act on immediately: an update, a mode the window is in.
 *
 * @param text the message, kept to one line and ellipsized.
 * @param tone picks the strip's background; [JwTone.Info] for news, [JwTone.Warning] for a mode
 * the user may want to leave.
 * @param onDismiss when non-null, adds a close button after the actions.
 * @param dismissLabel the close button's tooltip and accessibility name, in the UI's language;
 * required together with [onDismiss].
 * @param icon an optional glyph before the text, drawn in the tone's content color.
 * @param actions [JwButton]s in the [JwButtonStyle.Text] style, placed after the text.
 */
@Composable
public fun JwBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: JwTone = JwTone.Info,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String? = null,
    icon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    require((onDismiss == null) == (dismissLabel == null)) { "onDismiss and dismissLabel go together" }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .background(tone.containerColor)
                .padding(horizontal = JwSpacing.large, vertical = JwSpacing.tiny),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        ) {
            CompositionLocalProvider(LocalContentColor provides tone.onContainerColor) {
                icon?.invoke()
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                actions?.invoke(this)
                if (onDismiss != null) {
                    JwIconButton(onClick = onDismiss, tooltip = dismissLabel, size = 24.dp) {
                        JwIcon(imageVector = JwIcons.Close, contentDescription = null)
                    }
                }
            }
        }
        JwHorizontalDivider()
    }
}
