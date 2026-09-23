package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwToolbarPreview() {
    JwTheme(darkTheme = false) {
        JwToolbar(
            title = "Network Inspector",
            actions = {
                JwIconButton(onClick = {}, tooltip = "Clear") {
                    JwIcon(imageVector = JwIcons.Close, contentDescription = null)
                }
            },
        )
    }
}
