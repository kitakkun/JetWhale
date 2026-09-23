package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwListItemPreview() {
    JwTheme(darkTheme = false) {
        JwListItem(
            text = "Network Inspector",
            selected = true,
            onClick = {},
            supportingText = "com.kitakkun.jetwhale.network",
            leadingContent = { JwIcon(imageVector = JwIcons.Check, contentDescription = null) },
            trailingContent = { JwTag(text = "MCP") },
        )
    }
}

@Preview
@Composable
private fun JwListItemWithContentPreview() {
    JwTheme(darkTheme = false) {
        JwListItem(selected = false, onClick = {}) {
            JwText(text = "GET", style = JwTheme.textStyles.code)
            JwText(text = "/api/users", modifier = Modifier.weight(1f))
            JwCountBadge(count = 3)
        }
    }
}
