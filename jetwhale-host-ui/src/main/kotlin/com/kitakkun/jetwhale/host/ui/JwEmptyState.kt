package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwEmptyState]. */
public object JwEmptyStateDefaults {
    /** The widest the description grows before wrapping, so a sentence stays a readable line. */
    public val textMaxWidth: Dp = 360.dp
}

/**
 * What a pane shows when it has nothing to show, centered in the available space.
 *
 * @param title a short statement of the situation: "No plugin selected".
 * @param description how to get content there, in a sentence or two.
 * @param icon a muted glyph above the title.
 * @param action the button that gets content there, when one exists.
 */
@Composable
public fun JwEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(JwSpacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(JwSpacing.medium, Alignment.CenterVertically),
    ) {
        if (icon != null) {
            CompositionLocalProvider(LocalJwContentColor provides JwTheme.colors.textSecondary) {
                icon()
            }
        }
        JwText(
            text = title,
            style = JwTheme.textStyles.subtitle,
            color = JwTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            JwText(
                text = description,
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = JwEmptyStateDefaults.textMaxWidth),
            )
        }
        if (action != null) {
            Column(modifier = Modifier.padding(top = JwSpacing.medium)) {
                action()
            }
        }
    }
}
