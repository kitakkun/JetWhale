package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwIconButtonPreview() {
    JwTheme(darkTheme = false) {
        JwIconButton(onClick = {}, tooltip = "Close", selected = true) {
            JwIcon(imageVector = JwIcons.Close, contentDescription = null)
        }
    }
}
