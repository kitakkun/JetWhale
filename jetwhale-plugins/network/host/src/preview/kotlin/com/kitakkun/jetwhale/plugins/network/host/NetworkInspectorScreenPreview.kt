package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.host.ui.rememberJwTableColumnState

@Preview
@Composable
private fun NetworkInspectorScreenPreview() {
    JwTheme(darkTheme = false) {
        NetworkInspectorScreen(
            transactions = previewTransactions(),
            mockRules = emptyList(),
            mockingEnabled = true,
            trafficSplitPaneState = rememberJwSplitPaneState(0.42f),
            trafficColumnState = rememberJwTableColumnState(),
            onClearTransactions = {},
            onToggleMocking = {},
            onMockRulesChanged = {},
        )
    }
}
