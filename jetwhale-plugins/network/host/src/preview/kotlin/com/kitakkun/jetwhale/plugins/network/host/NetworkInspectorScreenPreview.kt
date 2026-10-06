package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState

@Preview
@Composable
private fun NetworkInspectorScreenPreview() {
    JwTheme(darkTheme = false) {
        NetworkInspectorScreen(
            transactions = previewTransactions(),
            mockRules = emptyList(),
            mockingEnabled = true,
            conditionRules = emptyList(),
            trafficSplitPaneState = rememberJwSplitPaneState(0.42f),
            onClearTransactions = {},
            onToggleMocking = {},
            onMockRulesChanged = {},
            onConditionRulesChanged = {},
        )
    }
}
