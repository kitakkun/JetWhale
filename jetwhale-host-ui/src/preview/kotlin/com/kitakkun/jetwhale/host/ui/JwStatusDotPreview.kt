package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwStatusDotPreview() {
    JwTheme(darkTheme = false) {
        JwStatusDot(tone = JwTone.Success)
    }
}
