package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwSnackbarHostPreview() {
    JwTheme(darkTheme = false) {
        val hostState = remember { JwSnackbarHostState() }
        LaunchedEffect(hostState) {
            hostState.showSnackbar(
                message = "The session disconnected",
                actionLabel = "Reconnect",
                duration = JwSnackbarDuration.Indefinite,
            )
        }
        JwSnackbarHost(hostState = hostState)
    }
}
