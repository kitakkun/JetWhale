package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwThemePreview() {
    JwTheme(colors = JwColors.light()) {
        JwText(text = "JetWhale", style = JwTheme.textStyles.title)
    }
}

@Preview
@Composable
private fun JwThemeDarkPreview() {
    JwTheme(darkTheme = true) {
        JwText(text = "JetWhale", style = JwTheme.textStyles.title)
    }
}
