package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import com.kitakkun.jetwhale.host.sdk.LocalJetWhalePluginStorage
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the horizontally scrollable URL in [TrafficTab]'s list rows: the list pane is too narrow
 * for a real URL, so the text scrolls sideways instead of being truncated. The row is a scrollable
 * inside a scrollable inside a clickable, so the gestures that must keep working (vertical wheel to
 * scroll the list, click to select the row) are asserted alongside the horizontal scroll itself.
 */
@OptIn(ExperimentalTestApi::class)
class TrafficRowUrlScrollTest {

    @Test
    fun `a long url in a list row is horizontally scrollable`() = runTrafficTab { _ ->
        val range = urlNode(TOP_ROW).horizontalScrollRange()
        assertEquals(0f, range.value(), "starts unscrolled")
        assertTrue(range.maxValue() > 0f, "content is wider than the row's viewport, maxValue=${range.maxValue()}")
    }

    @Test
    fun `a horizontal wheel over the url scrolls the url`() = runTrafficTab { _ ->
        val url = urlNode(TOP_ROW)
        url.performMouseInput {
            moveTo(center)
            repeat(WHEEL_NOTCHES) { scroll(1f, ScrollWheel.Horizontal) }
        }
        waitUntil("the url scrolls right") { url.horizontalScrollRange().value() > 0f }
    }

    @Test
    fun `a vertical wheel over the url scrolls the list`() = runTrafficTab(rows = LONG_LIST) { _ ->
        val list = onNode(hasScrollToIndexAction())
        assertEquals(0f, list.verticalScrollRange().value(), "list starts at the top")

        urlNode(TOP_ROW).performMouseInput {
            moveTo(center)
            repeat(WHEEL_NOTCHES) { scroll(1f, ScrollWheel.Vertical) }
        }

        // Asserted on the list rather than on the row: how far one notch travels is platform
        // specific, and the row the wheel landed on may itself have scrolled out of the viewport.
        waitUntil("the list scrolls down") { list.verticalScrollRange().value() > 0f }
    }

    @Test
    fun `a vertical wheel does not scroll the url sideways`() = runTrafficTab(rows = SHORT_LIST) { _ ->
        // A list short enough to need no vertical scrolling keeps the row in place, so the same URL
        // node can be re-read after the wheel regardless of how far a notch would have travelled.
        val url = urlNode(TOP_ROW)
        url.performMouseInput {
            moveTo(center)
            repeat(WHEEL_NOTCHES) { scroll(1f, ScrollWheel.Vertical) }
        }
        waitForIdle()
        assertEquals(0f, url.horizontalScrollRange().value())
    }

    @Test
    fun `clicking the url selects its row`() = runTrafficTab { selected ->
        urlNode(TOP_ROW).performClick()
        waitForIdle()
        assertEquals(txId(TOP_ROW), selected())
    }
}

/** Enough rows that the list can scroll vertically well past the viewport. */
private const val LONG_LIST = 60

/** Few enough rows that the list fits the viewport and cannot scroll at all. */
private const val SHORT_LIST = 3

/** Mouse wheel notches to send; one notch alone is a sub-pixel move under smooth scrolling. */
private const val WHEEL_NOTCHES = 10

/**
 * [TrafficTab] renders newest-first, and the fixtures are built newest-last, so index 0 owns the
 * topmost row.
 */
private const val TOP_ROW = 0

private fun txId(index: Int) = "tx-$index"

/**
 * Long enough to overflow the list pane at any plausible split position, and index-tagged so each
 * row is individually addressable.
 */
private fun url(index: Int) = "https://example.com/a/deliberately/long/path/that/no/list/pane/" +
    "is/wide/enough/to/show/items/$index?first=1&second=2&third=3"

/** Built newest-first so that [TOP_ROW] is index 0 once [TrafficTab] reverses the list. */
private fun transaction(index: Int, rows: Int) = HttpTransaction(
    request = CapturedHttpRequest(
        txId = txId(index),
        method = "GET",
        url = url(index),
        timestampMs = (rows - index).toLong(),
    ),
    response = CapturedHttpResponse(
        txId = txId(index),
        statusCode = 200,
        statusDescription = "OK",
        durationMs = 12L,
    ),
)

/**
 * Renders [TrafficTab] with [rows] transactions at a fixed size and runs [block] against it. The
 * lambda receives a getter for the most recently selected transaction id.
 */
@OptIn(ExperimentalTestApi::class)
private fun runTrafficTab(
    rows: Int = LONG_LIST,
    block: suspend ComposeUiTest.(selected: () -> String?) -> Unit,
) = runComposeUiTest {
    var selected: String? = null
    setContent {
        CompositionLocalProvider(LocalJetWhalePluginStorage provides InMemoryPluginStorage()) {
            JwTheme(darkTheme = false) {
                // Wide enough to clear ListMinWidth + DetailMinWidth, and tall enough that LONG_LIST
                // overflows the viewport while SHORT_LIST does not.
                Box(Modifier.requiredSize(width = 900.dp, height = 600.dp)) {
                    TrafficTab(
                        transactions = List(rows) { transaction(index = rows - 1 - it, rows = rows) },
                        selectedTxId = null,
                        onSelectTx = { selected = it },
                        onClear = {},
                        onCreateMock = {},
                    )
                }
            }
        }
    }
    block { selected }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.urlNode(index: Int): SemanticsNodeInteraction = onNodeWithText(url(index))

private fun SemanticsNodeInteraction.horizontalScrollRange(): ScrollAxisRange = fetchSemanticsNode()
    .config[SemanticsProperties.HorizontalScrollAxisRange]

private fun SemanticsNodeInteraction.verticalScrollRange(): ScrollAxisRange = fetchSemanticsNode()
    .config[SemanticsProperties.VerticalScrollAxisRange]

/** Minimal in-memory [JetWhalePluginStorage] so `rememberPersistent` has something to bind to. */
@Suppress("UNCHECKED_CAST")
private class InMemoryPluginStorage : JetWhalePluginStorage {
    private val values = MutableStateFlow<Map<String, Any?>>(emptyMap())

    override suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>) {
        values.update { it + (key to value) }
    }

    override suspend fun <T> get(key: String, serializer: KSerializer<T>): T? = values.value[key] as T?

    override fun <T> getFlow(key: String, serializer: KSerializer<T>): Flow<T?> = values.map { it[key] as T? }

    override suspend fun contains(key: String): Boolean = values.value.containsKey(key)

    override suspend fun remove(key: String) {
        values.update { it - key }
    }

    override suspend fun clear() {
        values.value = emptyMap()
    }

    override val keysFlow: Flow<Set<String>> get() = values.map { it.keys }
}
