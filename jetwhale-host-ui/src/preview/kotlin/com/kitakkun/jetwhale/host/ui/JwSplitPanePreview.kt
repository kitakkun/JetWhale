package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
private fun JwSplitPanePreview() {
    JwTheme(darkTheme = false) {
        JwSplitPane(
            first = { JwText(text = "List") },
            second = { JwText(text = "Detail") },
            modifier = Modifier
                .width(320.dp)
                .height(160.dp),
            firstMinSize = 40.dp,
            secondMinSize = 40.dp,
        )
    }
}
