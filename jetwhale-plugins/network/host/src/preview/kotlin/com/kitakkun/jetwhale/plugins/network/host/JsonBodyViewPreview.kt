package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme

@Preview
@Composable
private fun BodyBlockPreview() {
    JwTheme(darkTheme = false) {
        BodyBlock(
            label = "body",
            body = """{"items":[{"id":1,"name":"first"}],"total":1}""",
            truncated = false,
        )
    }
}
