package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.network.protocol.BodyEncoding
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TrafficContextMenuTest {
    @Test
    fun `right-clicking a transaction with text bodies offers cURL the URL and both bodies`() = runComposeUiTest {
        setTraffic(responseEncoding = BodyEncoding.TEXT)

        onNodeWithText(URL).performMouseInput { rightClick() }

        onNodeWithText("Copy as cURL").assertExists()
        onNodeWithText("Copy URL").assertExists()
        onNodeWithText("Copy request body").assertExists()
        onNodeWithText("Copy response body").assertExists()
    }

    @Test
    fun `a binary response body is not offered as text to copy`() = runComposeUiTest {
        setTraffic(responseEncoding = BodyEncoding.BASE64)

        onNodeWithText(URL).performMouseInput { rightClick() }

        onNodeWithText("Copy as cURL").assertExists()
        onNodeWithText("Copy request body").assertExists()
        onNodeWithText("Copy response body").assertDoesNotExist()
    }
}

private const val URL = "https://example.com/items"

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.setTraffic(responseEncoding: BodyEncoding) {
    val transaction = HttpTransaction(
        request = CapturedHttpRequest(txId = "tx-1", method = "POST", url = URL, timestampMs = 1L, body = """{"name":"item"}"""),
        response = CapturedHttpResponse(
            txId = "tx-1",
            statusCode = 200,
            statusDescription = "OK",
            durationMs = 12L,
            body = if (responseEncoding == BodyEncoding.BASE64) "iVBORw0KGgo=" else """{"id":1}""",
            bodyEncoding = responseEncoding,
        ),
    )
    setContent {
        JwTheme(darkTheme = false) {
            Box(Modifier.requiredSize(width = 900.dp, height = 600.dp)) {
                TrafficTab(
                    transactions = listOf(transaction),
                    selectedTxId = null,
                    splitPaneState = rememberJwSplitPaneState(0.42f),
                    onSelectTx = {},
                    onClear = {},
                    onCreateMock = {},
                )
            }
        }
    }
}
