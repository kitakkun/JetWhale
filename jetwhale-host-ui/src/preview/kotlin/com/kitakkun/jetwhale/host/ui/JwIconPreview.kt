package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwIconPreview() {
    JwTheme(darkTheme = false) {
        JwIcon(imageVector = JwIcons.Search, contentDescription = "Search")
    }
}

@Preview
@Composable
private fun JwIconFromPainterPreview() {
    JwTheme(darkTheme = false) {
        JwIcon(painter = rememberVectorPainter(JwIcons.Copy), contentDescription = "Copy")
    }
}
