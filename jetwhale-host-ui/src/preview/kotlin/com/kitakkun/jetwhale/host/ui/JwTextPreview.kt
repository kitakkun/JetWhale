package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwTextPreview() {
    JwTheme(darkTheme = false) {
        JwText(text = "Requests captured on this session")
    }
}

@Preview
@Composable
private fun JwTextAnnotatedPreview() {
    JwTheme(darkTheme = false) {
        JwText(text = AnnotatedString("GET /api/users"), style = JwTheme.textStyles.code)
    }
}
