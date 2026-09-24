package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwBannerPreview() {
    JwTheme(darkTheme = false) {
        JwBanner(
            text = "A newer version is available",
            icon = { JwIcon(imageVector = JwIcons.Check, contentDescription = null) },
            actions = { JwButton(text = "View", onClick = {}, style = JwButtonStyle.Text) },
        )
    }
}

@Preview
@Composable
private fun JwBannerDismissiblePreview() {
    JwTheme(darkTheme = false) {
        JwBanner(
            text = "Following the AI agent",
            onDismiss = {},
            dismissLabel = "Dismiss",
            tone = JwTone.Warning,
        )
    }
}
