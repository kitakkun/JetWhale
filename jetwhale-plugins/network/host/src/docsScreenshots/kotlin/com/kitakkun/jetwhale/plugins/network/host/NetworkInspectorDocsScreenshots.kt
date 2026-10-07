package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShot
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShotRecorder
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.PluginSceneSurface
import com.kitakkun.jetwhale.tools.docsscreenshots.mouseClickThenMovePointerAway
import com.kitakkun.jetwhale.tools.docsscreenshots.onSurface
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NetworkInspectorDocsScreenshots {
    private val recorder = DocsShotRecorder.forImagesDirectoryProperty()

    @Test
    fun `traffic with a JSON response selected`() = recorder.record(
        DocsShot(page = PAGE, name = "traffic", surfaceSize = DpSize(688.dp, 420.dp), density = 2f, displayWidth = 688),
    ) { darkTheme ->
        setNetworkInspector(darkTheme)
        onNodeWithText(ITEMS_URL).mouseClickThenMovePointerAway()
        onNodeWithText("items  [2]").mouseClickThenMovePointerAway()
        onNodeWithText("[0]  {3}").mouseClickThenMovePointerAway()
        onSurface()
    }

    @Test
    fun `the context menu of a transaction`() = recorder.record(
        DocsShot(page = PAGE, name = "context-menu", surfaceSize = DpSize(688.dp, 320.dp), density = 2f, displayWidth = 688),
    ) { darkTheme ->
        setNetworkInspector(darkTheme)
        onNodeWithText(CART_URL).performMouseInput { rightClick(center) }
        onSurface()
    }

    @Test
    fun `the mock rules list`() = recorder.record(
        DocsShot(page = PAGE, name = "mocks", surfaceSize = DpSize(688.dp, 300.dp), density = 2f, displayWidth = 688),
    ) { darkTheme ->
        setNetworkInspector(darkTheme)
        onNodeWithText("Mocks").mouseClickThenMovePointerAway()
        onSurface()
    }
}

/** The list wider than its default, so the URLs show their paths at the page's width. */
@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.setNetworkInspector(darkTheme: Boolean) {
    setContent {
        PluginSceneSurface(darkTheme = darkTheme, storage = InMemoryPluginStorage(mapOf("traffic.splitPosition" to "0.56"))) {
            NetworkInspectorScreenRoot(
                transactions = TRANSACTIONS,
                mockRules = MOCK_RULES,
                mockingEnabled = true,
                onClearTransactions = {},
                onToggleMocking = {},
                onMockRulesChanged = {},
            )
        }
    }
}

private const val PAGE = "network-inspector"

private const val ITEMS_URL = "https://example.com/api/items?page=1&size=20"

private const val CART_URL = "https://example.com/api/cart"

private val JSON_HEADERS = mapOf("Content-Type" to listOf("application/json; charset=utf-8"))

/** Oldest first, as the agent reports them; the list shows the newest on top. */
private val TRANSACTIONS = listOf(
    HttpTransaction(
        request = CapturedHttpRequest(txId = "tx-1", method = "GET", url = "https://example.com/api/session", timestampMs = 0),
        response = CapturedHttpResponse(txId = "tx-1", statusCode = 200, statusDescription = "OK", headers = JSON_HEADERS, body = """{"signedIn":true}""", durationMs = 64),
    ),
    HttpTransaction(
        request = CapturedHttpRequest(
            txId = "tx-2",
            method = "GET",
            url = ITEMS_URL,
            headers = mapOf("Accept" to listOf("application/json")),
            timestampMs = 1,
        ),
        response = CapturedHttpResponse(
            txId = "tx-2",
            statusCode = 200,
            statusDescription = "OK",
            headers = JSON_HEADERS,
            body = """{"items":[{"id":41,"name":"Blue mug","price":12.5},{"id":42,"name":"Notebook","price":4.0}],"page":1,"total":2}""",
            durationMs = 142,
        ),
    ),
    HttpTransaction(
        request = CapturedHttpRequest(txId = "tx-3", method = "GET", url = "https://example.com/images/banner.png", timestampMs = 2),
        response = CapturedHttpResponse(txId = "tx-3", statusCode = 200, statusDescription = "OK", headers = mapOf("Content-Type" to listOf("image/png")), durationMs = 88),
    ),
    HttpTransaction(
        request = CapturedHttpRequest(txId = "tx-4", method = "GET", url = "https://example.com/api/recommendations", timestampMs = 3),
        response = CapturedHttpResponse(
            txId = "tx-4",
            statusCode = 503,
            statusDescription = "Service Unavailable",
            headers = JSON_HEADERS,
            body = """{"error":"maintenance"}""",
            durationMs = 300,
            fromMock = true,
        ),
    ),
    HttpTransaction(
        request = CapturedHttpRequest(
            txId = "tx-5",
            method = "POST",
            url = CART_URL,
            headers = JSON_HEADERS,
            body = """{"itemId":42,"quantity":1}""",
            timestampMs = 4,
        ),
        response = CapturedHttpResponse(txId = "tx-5", statusCode = 201, statusDescription = "Created", headers = JSON_HEADERS, body = """{"cartId":7,"items":1}""", durationMs = 210),
    ),
    HttpTransaction(
        request = CapturedHttpRequest(txId = "tx-6", method = "GET", url = "https://example.com/api/profile", timestampMs = 5),
        response = CapturedHttpResponse(txId = "tx-6", statusCode = 401, statusDescription = "Unauthorized", headers = JSON_HEADERS, body = """{"error":"token expired"}""", durationMs = 55),
    ),
    HttpTransaction(
        request = CapturedHttpRequest(txId = "tx-7", method = "GET", url = "https://example.com/api/search?q=whale", timestampMs = 6),
        failure = HttpRequestFailure(txId = "tx-7", message = "Request timeout has expired", durationMs = 10_000),
    ),
)

private val MOCK_RULES = listOf(
    MockRule(
        id = "rule-1",
        name = "Recommendations are down",
        matcher = MockMatcher(method = "GET", urlPattern = "/api/recommendations", matchType = MockMatchType.CONTAINS),
        response = MockResponseSpec(statusCode = 503, headers = mapOf("Content-Type" to "application/json"), body = """{"error":"maintenance"}""", delayMs = 300),
    ),
    MockRule(
        id = "rule-2",
        name = "Empty cart",
        enabled = false,
        matcher = MockMatcher(method = "GET", urlPattern = "https://example.com/api/cart", matchType = MockMatchType.EXACT),
        response = MockResponseSpec(headers = mapOf("Content-Type" to "application/json"), body = """{"items":[]}"""),
    ),
    MockRule(
        id = "rule-3",
        name = "Slow search",
        matcher = MockMatcher(urlPattern = "/api/search\\?q=.*", matchType = MockMatchType.REGEX),
        response = MockResponseSpec(headers = mapOf("Content-Type" to "application/json"), body = """{"results":[]}""", delayMs = 3_000),
    ),
)
