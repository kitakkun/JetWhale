package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule

@Preview
@Composable
private fun MocksTabPreview() {
    JwTheme(darkTheme = false) {
        MocksTab(
            rules = listOf(
                MockRule(
                    id = "rule-1",
                    name = "Items are unavailable",
                    enabled = true,
                    matcher = MockMatcher(method = "GET", urlPattern = "/api/items", matchType = MockMatchType.CONTAINS),
                    response = MockResponseSpec(statusCode = 503),
                ),
            ),
            mockingEnabled = true,
            onToggleMocking = {},
            onChanged = {},
        )
    }
}
