package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
private fun JwHorizontalDividerPreview() {
    JwTheme(darkTheme = false) {
        JwHorizontalDivider()
    }
}

@Preview
@Composable
private fun JwVerticalDividerPreview() {
    JwTheme(darkTheme = false) {
        JwVerticalDivider(modifier = Modifier.height(24.dp))
    }
}
