package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwEmptyStatePreview() {
    JwTheme(darkTheme = false) {
        JwEmptyState(
            title = "No plugin selected",
            description = "Pick a session and a plugin in the sidebar.",
            icon = { JwIcon(imageVector = JwIcons.Search, contentDescription = null) },
            action = { JwButton(text = "Open settings", onClick = {}, style = JwButtonStyle.Primary) },
        )
    }
}
