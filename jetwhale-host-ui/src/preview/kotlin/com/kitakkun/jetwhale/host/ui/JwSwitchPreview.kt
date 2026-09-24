package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwSwitchPreview() {
    JwTheme(darkTheme = false) {
        JwSwitch(checked = true, onCheckedChange = {}, contentDescription = "Follow the agent")
    }
}
