package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwStatusLinePreview() {
    JwTheme(darkTheme = false) {
        JwStatusLine(
            text = "3 roots · 120 nodes · 12 ms",
            trailingContent = { JwCountBadge(count = 120) },
        )
    }
}
