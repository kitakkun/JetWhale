package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.host.ui.rememberJwTableColumnState

@Preview
@Composable
private fun TrafficTabPreview() {
    JwTheme(darkTheme = false) {
        val transactions = previewTransactions()
        TrafficTab(
            transactions = transactions,
            selectedTxId = transactions.first().txId,
            splitPaneState = rememberJwSplitPaneState(PREVIEW_SPLIT_POSITION),
            columnState = rememberJwTableColumnState(),
            onSelectTx = {},
            onClear = {},
            onCreateMock = {},
        )
    }
}

private const val PREVIEW_SPLIT_POSITION = 0.42f
