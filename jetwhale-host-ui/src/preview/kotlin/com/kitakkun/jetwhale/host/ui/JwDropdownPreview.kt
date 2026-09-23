package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwDropdownButtonPreview() {
    JwTheme(darkTheme = false) {
        JwDropdownButton(
            text = "Maven Central",
            expanded = false,
            onExpandedChange = {},
            trailingIcon = { JwStatusDot(tone = JwTone.Success) },
            menu = {},
        )
    }
}

@Preview
@Composable
private fun JwDropdownMenuPreview() {
    JwTheme(darkTheme = false) {
        JwDropdownMenu(expanded = true, onDismissRequest = {}) {
            JwMenuItem(text = "Copy as cURL", onClick = {})
        }
    }
}

@Preview
@Composable
private fun JwMenuItemPreview() {
    JwTheme(darkTheme = false) {
        JwMenuItem(text = "Clear session", onClick = {}, selected = true, tone = JwTone.Error)
    }
}
