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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Sizes of a [JwDialog]. */
public object JwDialogDefaults {
    /** The default width: room for a form with labels above its fields. */
    public val width: Dp = 480.dp
}

/** Shadow under a dialog. */
private val DialogShadowElevation = 12.dp

/**
 * A modal dialog with a title bar that carries a close button, so there is always a visible way
 * out, and a footer for the [dismissButton] and [confirmButton] in that order. Escape and a click
 * outside also dismiss it. [text] — the body, named after Material's `AlertDialog` — is laid out as
 * a column with the dialog's padding, and shrinks to whatever height the window leaves after the
 * title bar and footer, so the buttons never fall off the bottom; wrap a body that can be tall in
 * a `Column` with `verticalScroll`.
 *
 * For a dialog that has no title bar — an image preview, a one-line confirmation — build on
 * [JwDialogSurface] instead and place your own close control.
 *
 * @param onDismissRequest called by the close button, Escape, and a click outside the dialog.
 * @param title shown in the title bar.
 * @param closeLabel the close button's tooltip and accessibility label, in the UI's language.
 * @param width the dialog's fixed width; the height follows the content.
 * @param confirmButton the action that completes the dialog, placed last in the footer.
 * @param dismissButton the action that abandons it, placed before [confirmButton].
 * @param text the body, laid out as a column with [JwSpacing.large] between children.
 */
@Composable
public fun JwDialog(
    onDismissRequest: () -> Unit,
    title: String,
    closeLabel: String,
    modifier: Modifier = Modifier,
    width: Dp = JwDialogDefaults.width,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    text: @Composable ColumnScope.() -> Unit,
) {
    JwDialogSurface(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        width = width,
    ) {
        JwToolbar(
            title = title,
            actions = {
                JwIconButton(onClick = onDismissRequest, tooltip = closeLabel) {
                    JwIcon(imageVector = JwIcons.Close, contentDescription = null)
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(JwSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.large),
            content = text,
        )
        if (confirmButton != null || dismissButton != null) {
            JwHorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JwSpacing.extraLarge, vertical = JwSpacing.large),
                horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke()
                confirmButton?.invoke()
            }
        }
    }
}

/**
 * The modal frame [JwDialog] is built on: a shadowed, bordered surface of [width] that Escape and a
 * click outside dismiss, with [content] laid out as a column and no padding, title or buttons of
 * its own. Use it directly for a dialog whose chrome does not fit the standard title-bar shape —
 * and give it a visible way to close, since nothing here adds one.
 *
 * @param onDismissRequest called on Escape and on a click outside the dialog.
 * @param width the dialog's fixed width; the height follows the content.
 * @param content the whole dialog, edge to edge.
 */
@Composable
public fun JwDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = JwDialogDefaults.width,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = JwShapes.large
        Column(
            modifier = modifier
                .width(width)
                .shadow(DialogShadowElevation, shape)
                .clip(shape)
                .background(JwTheme.colors.elevatedBackground, shape)
                .border(JwMetrics.borderWidth, JwTheme.colors.border, shape),
            content = content,
        )
    }
}
