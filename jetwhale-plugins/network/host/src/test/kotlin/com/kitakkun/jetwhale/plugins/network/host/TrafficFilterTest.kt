package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TrafficFilterTest {

    @Test
    fun `a request that matches the typed filter appears as soon as it is captured`() = runFilteredTrafficTab { transactions ->
        transactions.add(pending(id = "tx-2", url = "https://example.com/users/2"))

        waitForIdle()
        onNodeWithText("https://example.com/users/2").assertExists()
    }

    @Test
    fun `a pending request under the typed filter shows its status once it completes`() = runFilteredTrafficTab { transactions ->
        onNodeWithText("···").assertExists()

        transactions[0] = transactions[0].copy(response = response(id = "tx-1"))

        waitForIdle()
        onNodeWithText("200").assertExists()
    }

    @Test
    fun `a request that does not match the typed filter stays hidden`() = runFilteredTrafficTab { transactions ->
        transactions.add(pending(id = "tx-2", url = "https://example.com/orders/2"))

        waitForIdle()
        onNodeWithText("https://example.com/orders/2").assertDoesNotExist()
    }
}

/**
 * Renders [TrafficTab] over a live list, as the plugin does, with "users" typed into the filter
 * and one pending request that matches it.
 */
@OptIn(ExperimentalTestApi::class)
private fun runFilteredTrafficTab(block: ComposeUiTest.(transactions: SnapshotStateList<HttpTransaction>) -> Unit) = runComposeUiTest {
    val transactions = mutableStateListOf(pending(id = "tx-1", url = "https://example.com/users/1"))
    setContent {
        JwTheme(darkTheme = false) {
            Box(Modifier.requiredSize(width = 900.dp, height = 600.dp)) {
                TrafficTab(
                    transactions = transactions,
                    selectedTxId = null,
                    splitPaneState = rememberJwSplitPaneState(0.42f),
                    onSelectTx = {},
                    onClear = {},
                    onCreateMock = {},
                )
            }
        }
    }
    onNode(hasSetTextAction()).performTextInput("users")
    waitForIdle()
    block(transactions)
}

private fun pending(id: String, url: String) = HttpTransaction(
    request = CapturedHttpRequest(txId = id, method = "GET", url = url, timestampMs = 0L),
)

private fun response(id: String) = CapturedHttpResponse(txId = id, statusCode = 200, statusDescription = "OK", durationMs = 12L)
