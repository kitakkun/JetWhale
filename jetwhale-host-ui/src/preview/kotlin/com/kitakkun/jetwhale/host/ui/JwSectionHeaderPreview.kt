package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwSectionHeaderPreview() {
    JwTheme(darkTheme = false) {
        JwSectionHeader(
            title = "Enabled plugins",
            count = 3,
            expanded = true,
            onToggleExpanded = {},
            trailing = { JwTag(text = "3 of 5") },
        )
    }
}
