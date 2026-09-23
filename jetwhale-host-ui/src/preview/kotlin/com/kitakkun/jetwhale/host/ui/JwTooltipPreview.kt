package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwTooltipPreview() {
    JwTheme(darkTheme = false) {
        JwTooltip(text = "Clear the session") {
            JwText(text = "Clear")
        }
    }
}
