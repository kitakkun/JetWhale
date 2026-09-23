package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwCountBadgePreview() {
    JwTheme(darkTheme = false) {
        JwCountBadge(count = 42, tone = JwTone.Warning)
    }
}
