package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwKeyValueRowPreview() {
    JwTheme(darkTheme = false) {
        JwKeyValueRow(
            key = "Content-Type",
            value = "application/json; charset=utf-8",
            monospace = true,
            trailingContent = {
                JwIconButton(onClick = {}, tooltip = "Copy", size = JwIconButtonDefaults.inlineSize) {
                    JwIcon(imageVector = JwIcons.Copy, contentDescription = null)
                }
            },
        )
    }
}
