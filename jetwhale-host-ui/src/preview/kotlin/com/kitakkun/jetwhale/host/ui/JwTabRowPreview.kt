package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwTabRowPreview() {
    JwTheme(darkTheme = false) {
        JwTabRow {
            JwTab(selected = true, onClick = {}, text = "Traffic", count = 12)
            JwTab(selected = false, onClick = {}, text = "Mocks")
        }
    }
}

@Preview
@Composable
private fun JwTabPreview() {
    JwTheme(darkTheme = false) {
        JwTab(selected = true, onClick = {}, text = "Traffic", count = 12, modifier = Modifier.height(JwTabRowDefaults.height))
    }
}
