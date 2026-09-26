package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState

@Preview
@Composable
private fun TrafficTabPreview() {
    JwTheme(darkTheme = false) {
        val transactions = previewTransactions()
        TrafficTab(
            transactions = transactions,
            selectedTxId = transactions.first().txId,
            splitPaneState = rememberJwSplitPaneState(PREVIEW_SPLIT_POSITION),
            onSelectTx = {},
            onClear = {},
            onCreateMock = {},
        )
    }
}

@Preview
@Composable
private fun StatusBadgePreview() {
    JwTheme(darkTheme = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            previewTransactions().forEach { StatusBadge(it) }
            MockChip()
        }
    }
}

private const val PREVIEW_SPLIT_POSITION = 0.42f
