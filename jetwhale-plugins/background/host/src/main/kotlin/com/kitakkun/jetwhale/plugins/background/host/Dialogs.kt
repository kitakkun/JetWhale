package com.kitakkun.jetwhale.plugins.background.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwSegmentedButtons
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget

/** Asks before work is cancelled; nothing a cancellation does can be undone. */
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
        closeLabel = "Keep",
        onDismissRequest = onDismiss,
        confirmButton = { JwButton(text = confirmLabel, style = JwButtonStyle.Primary, tone = JwTone.Error, onClick = onConfirm) },
        dismissButton = { JwButton(text = "Keep", onClick = onDismiss) },
    ) {
        JwText(text = message)
    }
}

/**
 * Cancels work by the unique name it was enqueued under. WorkManager does not report unique names,
 * so the name is typed rather than picked.
 */
@Composable
internal fun CancelUniqueWorkDialog(
    sourceNames: List<String>,
    onCancel: (source: String, target: CancelTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    var source by remember { mutableStateOf(sourceNames.first()) }
    var uniqueName by remember { mutableStateOf("") }
    JwDialog(
        title = "Cancel unique work",
        closeLabel = "Close",
        onDismissRequest = onDismiss,
        confirmButton = {
            JwButton(
                text = "Cancel work",
                style = JwButtonStyle.Primary,
                tone = JwTone.Error,
                enabled = uniqueName.isNotBlank(),
                onClick = { onCancel(source, CancelTarget.ByUniqueName(uniqueName.trim())) },
            )
        },
        dismissButton = { JwButton(text = "Close", onClick = onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
            if (sourceNames.size > 1) JwSegmentedButtons(options = sourceNames, selected = source, onSelect = { source = it }, label = { it })
            JwText(text = "The name passed to enqueueUniqueWork or enqueueUniquePeriodicWork. The scheduler does not list these names, so type it.")
            JwTextField(value = uniqueName, onValueChange = { uniqueName = it }, placeholder = "Unique work name", modifier = Modifier.fillMaxWidth())
        }
    }
}
