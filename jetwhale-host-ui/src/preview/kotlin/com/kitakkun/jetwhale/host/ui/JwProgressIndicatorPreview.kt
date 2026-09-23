package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwProgressIndicatorPreview() {
    JwTheme(darkTheme = false) {
        JwProgressIndicator(size = JwProgressIndicatorDefaults.largeSize)
    }
}
