package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TrafficRowMockTagTest {

    @Test
    fun `only a row answered by a mock carries the MOCK tag`() = runComposeUiTest {
        setContent {
            JwTheme(darkTheme = false) {
                Box(Modifier.requiredSize(width = 900.dp, height = 600.dp)) {
                    TrafficTab(
                        transactions = listOf(
                            transaction(txId = "tx-1", url = UNMOCKED_URL, fromMock = false),
                            transaction(txId = "tx-2", url = MOCKED_URL, fromMock = true),
                        ),
                        selectedTxId = null,
                        splitPaneState = rememberJwSplitPaneState(0.42f),
                        onSelectTx = {},
                        onClear = {},
                        onCreateMock = {},
                    )
                }
            }
        }

        onNodeWithText(MOCKED_URL).assert(hasText("MOCK"))
        onNodeWithText(UNMOCKED_URL).assert(!hasText("MOCK"))
    }
}

private const val MOCKED_URL = "https://example.com/mocked"
private const val UNMOCKED_URL = "https://example.com/unmocked"

private fun transaction(txId: String, url: String, fromMock: Boolean) = HttpTransaction(
    request = CapturedHttpRequest(txId = txId, method = "GET", url = url, timestampMs = 0L),
    response = CapturedHttpResponse(txId = txId, statusCode = 200, statusDescription = "OK", durationMs = 12L, fromMock = fromMock),
)
