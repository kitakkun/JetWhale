package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwSegmentedButtonsPreview() {
    JwTheme(darkTheme = false) {
        JwSegmentedButtons(
            options = listOf("Tree", "Raw"),
            selected = "Tree",
            onSelect = {},
            label = { it },
        )
    }
}
