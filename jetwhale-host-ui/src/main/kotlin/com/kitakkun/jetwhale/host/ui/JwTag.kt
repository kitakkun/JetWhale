package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** How a [JwTag] is drawn. */
public enum class JwTagStyle {
    /** Colored text on a hairline outline: the default, quiet enough to repeat on every row. */
    Outlined,

    /** Colored text on the tone's soft container. */
    Tinted,

    /** Contrasting text on the tone's strong color: reserved for the state that must be seen. */
    Filled,
}

/**
 * A small inline label — an HTTP method, a status code, "MCP", "mocked". Height 18dp, so it fits on
 * a [JwListItem] row without stretching it. Pass [onClick] to make it a button.
 */
@Composable
public fun JwTag(
    text: String,
    modifier: Modifier = Modifier,
    tone: JwTone = JwTone.Neutral,
    style: JwTagStyle = JwTagStyle.Outlined,
    onClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.extraSmall
    val background = when (style) {
        JwTagStyle.Outlined -> null
        JwTagStyle.Tinted -> tone.containerColor
        JwTagStyle.Filled -> tone.color
    }
    val contentColor = when (style) {
        JwTagStyle.Outlined -> tone.color
        JwTagStyle.Tinted -> tone.onContainerColor
        JwTagStyle.Filled -> tone.onColor
    }
    Row(
        modifier = modifier
            .height(18.dp)
            .clip(shape)
            .then(if (background != null) Modifier.background(background, shape) else Modifier)
            .then(if (style == JwTagStyle.Outlined) Modifier.border(JwMetrics.borderWidth, tone.color.copy(alpha = 0.6f), shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = JwSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            leadingIcon?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            trailingIcon?.invoke()
        }
    }
}
