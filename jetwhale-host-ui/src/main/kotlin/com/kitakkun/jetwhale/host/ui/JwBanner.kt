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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwBanner]. */
public object JwBannerDefaults {
    /** The strip's minimum height. */
    public val minHeight: Dp = 32.dp
}

/** Side of the close button; between the inline size and a full control, to suit the strip. */
private val DismissButtonSize = 24.dp

/**
 * A one-line strip across the top of a pane that reports something the user should know but not
 * act on immediately: an update, a mode the window is in. This overload has no close button; use
 * the one with [onDismiss] for a banner the user can put away.
 *
 * @param text the message, kept to one line and ellipsized.
 * @param tone picks the strip's background; [JwTone.Info] for news, [JwTone.Warning] for a mode
 * the user may want to leave.
 * @param icon an optional glyph before the text, drawn in the tone's content color.
 * @param actions [JwButton]s in the [JwButtonStyle.Text] style, placed after the text.
 */
@Composable
public fun JwBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: JwTone = JwTone.Info,
    icon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    BannerStrip(text = text, modifier = modifier, tone = tone, icon = icon, actions = actions, dismiss = null)
}

/**
 * A dismissible [JwBanner]: the same strip with a close button after the actions.
 *
 * @param text the message, kept to one line and ellipsized.
 * @param onDismiss what the close button does — usually hides the banner.
 * @param dismissLabel the close button's tooltip and accessibility name, in the UI's language.
 * @param tone picks the strip's background.
 * @param icon an optional glyph before the text, drawn in the tone's content color.
 * @param actions [JwButton]s in the [JwButtonStyle.Text] style, placed between the text and the
 * close button.
 */
@Composable
public fun JwBanner(
    text: String,
    onDismiss: () -> Unit,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    tone: JwTone = JwTone.Info,
    icon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    BannerStrip(text = text, modifier = modifier, tone = tone, icon = icon, actions = actions, dismiss = onDismiss to dismissLabel)
}

@Composable
private fun BannerStrip(
    text: String,
    modifier: Modifier,
    tone: JwTone,
    icon: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    dismiss: Pair<() -> Unit, String>?,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = JwBannerDefaults.minHeight)
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
                if (dismiss != null) {
                    val (onDismiss, dismissLabel) = dismiss
                    JwIconButton(onClick = onDismiss, tooltip = dismissLabel, size = DismissButtonSize) {
                        JwIcon(imageVector = JwIcons.Close, contentDescription = null)
                    }
                }
            }
        }
        JwHorizontalDivider()
    }
}
