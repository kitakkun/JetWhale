package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTone

/** Asks before something is removed from the app's storage; nothing here can be undone. */
@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    JwDialog(
        title = title,
        closeLabel = "Cancel",
        onDismissRequest = onDismiss,
        confirmButton = { JwButton(text = "Delete", style = JwButtonStyle.Primary, tone = JwTone.Error, onClick = onConfirm) },
        dismissButton = { JwButton(text = "Cancel", onClick = onDismiss) },
    ) {
        JwText(text = message)
    }
}
