package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TrafficContextMenuTest {
    @Test
    fun `right-clicking a transaction with text bodies offers cURL the URL and both bodies`() = runComposeUiTest {
        setTraffic(request = TEXT_REQUEST, response = TEXT_RESPONSE)

        onNodeWithText(URL).performMouseInput { rightClick() }

        onNodeWithText("Copy as cURL").assertExists()
        onNodeWithText("Copy URL").assertExists()
        onNodeWithText("Copy request body").assertExists()
        onNodeWithText("Copy response body").assertExists()
    }

    @Test
    fun `a binary response body is not offered as text to copy`() = runComposeUiTest {
        setTraffic(request = TEXT_REQUEST, response = "iVBORw0KGgo=" to BodyEncoding.BASE64)

        onNodeWithText(URL).performMouseInput { rightClick() }

        onNodeWithText("Copy as cURL").assertExists()
        onNodeWithText("Copy request body").assertExists()
        onNodeWithText("Copy response body").assertDoesNotExist()
    }

    @Test
    fun `a binary request body is not offered as text to copy`() = runComposeUiTest {
        setTraffic(request = "AAECAw==" to BodyEncoding.BASE64, response = TEXT_RESPONSE)

        onNodeWithText(URL).performMouseInput { rightClick() }

        onNodeWithText("Copy as cURL").assertExists()
        onNodeWithText("Copy request body").assertDoesNotExist()
        onNodeWithText("Copy response body").assertExists()
    }

    @Test
    fun `a body that was not captured is not offered as text to copy`() {
        UNCAPTURED_BODY_MARKERS.forEach { marker ->
            runComposeUiTest {
                setTraffic(request = marker to BodyEncoding.TEXT, response = marker to BodyEncoding.TEXT)

                onNodeWithText(URL).performMouseInput { rightClick() }

                onNodeWithText("Copy as cURL").assertExists()
                assertTrue(onAllNodesWithText("Copy request body").fetchSemanticsNodes().isEmpty(), marker)
                assertTrue(onAllNodesWithText("Copy response body").fetchSemanticsNodes().isEmpty(), marker)
            }
        }
    }

    @Test
    fun `a body that is a single XML or HTML element is offered as text to copy`() {
        SINGLE_ELEMENT_BODIES.forEach { body ->
            runComposeUiTest {
                setTraffic(request = body to BodyEncoding.TEXT, response = body to BodyEncoding.TEXT)

                onNodeWithText(URL).performMouseInput { rightClick() }

                assertEquals(1, onAllNodesWithText("Copy request body").fetchSemanticsNodes().size, body)
                assertEquals(1, onAllNodesWithText("Copy response body").fetchSemanticsNodes().size, body)
            }
        }
    }
}

private const val URL = "https://example.com/items"

private val TEXT_REQUEST = """{"name":"item"}""" to BodyEncoding.TEXT

private val TEXT_RESPONSE = """{"id":1}""" to BodyEncoding.TEXT

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.setTraffic(request: Pair<String, BodyEncoding>, response: Pair<String, BodyEncoding>) {
    val transaction = HttpTransaction(
        request = CapturedHttpRequest(
            txId = "tx-1",
            method = "POST",
            url = URL,
            timestampMs = 1L,
            body = request.first,
            bodyEncoding = request.second,
        ),
        response = CapturedHttpResponse(
            txId = "tx-1",
            statusCode = 200,
            statusDescription = "OK",
            durationMs = 12L,
            body = response.first,
            bodyEncoding = response.second,
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
