package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwTagPreview() {
    JwTheme(darkTheme = false) {
        JwTag(
            text = "MCP",
            tone = JwTone.Accent,
            style = JwTagStyle.Tinted,
            trailingIcon = { JwIcon(imageVector = JwIcons.ChevronRight, contentDescription = null) },
        )
    }
}
