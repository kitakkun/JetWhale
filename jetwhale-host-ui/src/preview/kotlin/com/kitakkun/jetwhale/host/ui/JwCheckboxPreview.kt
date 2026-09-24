package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwCheckboxPreview() {
    JwTheme(darkTheme = false) {
        JwCheckbox(checked = true, onCheckedChange = {}, label = "Follow the agent")
    }
}

@Preview
@Composable
private fun JwTriStateCheckboxPreview() {
    JwTheme(darkTheme = false) {
        JwTriStateCheckbox(state = ToggleableState.Indeterminate, onClick = {}, label = "All plugins")
    }
}
