package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A modal dialog with a title bar that carries a close button, so there is always a visible way
 * out, and a footer for the [dismissButton] and [confirmButton] in that order. Escape and a click
 * outside also dismiss it. [content] is laid out as a column with the dialog's padding; long
 * content scrolls only if the caller makes it.
 *
 * @param closeLabel the close button's tooltip and accessibility label, in the UI's language.
 */
@Composable
public fun JwDialog(
    title: String,
    onDismissRequest: () -> Unit,
    closeLabel: String,
    modifier: Modifier = Modifier,
    width: Dp = 480.dp,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = MaterialTheme.shapes.large
        Column(
            modifier = modifier
                .width(width)
                .shadow(12.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer, shape)
                .border(JwMetrics.borderWidth, JwTheme.colors.border, shape),
        ) {
            JwToolbar(
                title = title,
                actions = {
                    JwIconButton(onClick = onDismissRequest, tooltip = closeLabel) {
                        JwIcon(imageVector = JwIcons.Close, contentDescription = closeLabel)
                    }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(JwSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(JwSpacing.lg),
                content = content,
            )
            if (confirmButton != null || dismissButton != null) {
                JwHorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JwSpacing.xl, vertical = JwSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(JwSpacing.md, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton?.invoke()
                }
            }
        }
    }
}
