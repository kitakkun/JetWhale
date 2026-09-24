package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwTreeRowPreview() {
    JwTheme(darkTheme = false) {
        JwTreeRow(
            text = "Column",
            depth = 1,
            expandable = true,
            expanded = true,
            selected = true,
            onClick = {},
            onToggleExpanded = {},
            trailingContent = { JwTag(text = "clickable", tone = JwTone.Accent) },
        )
    }
}
