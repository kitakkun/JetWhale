package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule

@Preview
@Composable
private fun ConditionsTabPreview() {
    JwTheme(darkTheme = false) {
        ConditionsTab(
            rules = listOf(
                NetworkConditionPreset.Fast3G.toRule(),
                NetworkConditionRule(
                    id = "images",
                    name = "Slow images",
                    enabled = true,
                    matcher = MockMatcher(urlPattern = "/images/"),
                    condition = NetworkConditionPreset.Edge.condition,
                ),
            ),
            onChanged = {},
        )
    }
}

@Preview
@Composable
private fun ConditionsTabEmptyPreview() {
    JwTheme(darkTheme = true) {
        ConditionsTab(rules = emptyList(), onChanged = {})
    }
}
