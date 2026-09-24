package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwCodeBlockPreview() {
    JwTheme(darkTheme = false) {
        JwCodeBlock(text = "{\n  \"id\": 42\n}", copyLabel = "Copy")
    }
}

@Preview
@Composable
private fun JwCodeBlockAnnotatedPreview() {
    JwTheme(darkTheme = false) {
        JwCodeBlock(text = AnnotatedString("GET /api/users"), wrap = true)
    }
}
