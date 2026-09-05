package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shows [text] in a small tooltip while the pointer rests on [content]. A null [text] renders the
 * content bare, so callers can pass an optional label straight through.
 *
 * @param text the tooltip's text, or null for no tooltip.
 * @param content the composable the tooltip describes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun JwTooltip(
    text: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (text == null) {
        content()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            PlainTooltip(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = JwSpacing.xxs),
                )
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
