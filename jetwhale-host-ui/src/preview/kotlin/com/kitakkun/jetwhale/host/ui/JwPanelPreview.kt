package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwPanelPreview() {
    JwTheme(darkTheme = false) {
        JwPanel(
            title = "Properties",
            headerActions = { JwButton(text = "Copy", onClick = {}, style = JwButtonStyle.Text) },
        ) {
            JwKeyValueRow(key = "id", value = "42", monospace = true)
        }
    }
}
