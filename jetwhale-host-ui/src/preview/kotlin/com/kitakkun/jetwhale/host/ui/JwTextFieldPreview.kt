package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwTextFieldPreview() {
    JwTheme(darkTheme = false) {
        JwTextField(value = "com.example", onValueChange = {}, placeholder = "Group id")
    }
}

@Preview
@Composable
private fun JwSearchFieldPreview() {
    JwTheme(darkTheme = false) {
        JwSearchField(value = "api/users", onValueChange = {}, clearLabel = "Clear", placeholder = "Filter")
    }
}
