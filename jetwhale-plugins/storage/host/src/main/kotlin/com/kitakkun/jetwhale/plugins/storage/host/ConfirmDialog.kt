package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTone

/**
 * Asks before an operation on the app's storage. [confirmTone] is [JwTone.Error] for one that
 * removes or replaces something, since that cannot be undone.
 */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    confirmTone: JwTone,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    JwDialog(
        title = title,
        closeLabel = "Cancel",
        onDismissRequest = onDismiss,
        confirmButton = { JwButton(text = confirmLabel, style = JwButtonStyle.Primary, tone = confirmTone, onClick = onConfirm) },
        dismissButton = { JwButton(text = "Cancel", onClick = onDismiss) },
    ) {
        JwText(text = message)
    }
}
