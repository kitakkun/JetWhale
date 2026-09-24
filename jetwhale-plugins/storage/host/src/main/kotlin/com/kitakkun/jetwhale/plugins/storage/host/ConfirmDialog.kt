package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTone

/** Asks before something in the app's storage is removed or replaced; neither can be undone. */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    JwDialog(
        title = title,
        closeLabel = "Cancel",
        onDismissRequest = onDismiss,
        confirmButton = { JwButton(text = confirmLabel, style = JwButtonStyle.Primary, tone = JwTone.Error, onClick = onConfirm) },
        dismissButton = { JwButton(text = "Cancel", onClick = onDismiss) },
    ) {
        JwText(text = message)
    }
}
