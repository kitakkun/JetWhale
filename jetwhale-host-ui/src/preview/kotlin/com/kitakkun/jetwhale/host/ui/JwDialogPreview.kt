package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwDialogPreview() {
    JwTheme(darkTheme = false) {
        JwDialog(
            onDismissRequest = {},
            title = "Remove plugin",
            closeLabel = "Close",
            confirmButton = { JwButton(text = "Remove", onClick = {}, tone = JwTone.Error) },
            dismissButton = { JwButton(text = "Cancel", onClick = {}) },
        ) {
            JwText(text = "The plugin is removed from this host only.")
        }
    }
}

@Preview
@Composable
private fun JwDialogSurfacePreview() {
    JwTheme(darkTheme = false) {
        JwDialogSurface(onDismissRequest = {}) {
            JwText(text = "A frame with no chrome of its own.")
        }
    }
}
