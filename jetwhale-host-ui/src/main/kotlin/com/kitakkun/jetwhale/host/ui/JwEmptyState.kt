package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
            CompositionLocalProvider(LocalContentColor provides JwTheme.colors.textDisabled) {
                icon()
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = JwTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = JwTheme.colors.textDisabled,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        if (action != null) {
            Column(modifier = Modifier.padding(top = JwSpacing.medium)) {
                action()
            }
        }
    }
}
