package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwButtonPreview() {
    JwTheme(darkTheme = false) {
        JwButton(text = "Save", onClick = {}, style = JwButtonStyle.Primary)
    }
}

@Preview
@Composable
private fun JwButtonWithContentPreview() {
    JwTheme(darkTheme = false) {
        JwButton(onClick = {}) {
            JwIcon(imageVector = JwIcons.Check, contentDescription = null)
            JwText(text = "Applied")
        }
    }
}
