package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
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
        val range = urlNode(newestIndex).horizontalScrollRange()
        assertEquals(0f, range.value(), "starts unscrolled")
        assertTrue(range.maxValue() > 0f, "content is wider than the row's viewport, maxValue=${range.maxValue()}")
    }

    @Test
    fun `a horizontal wheel over the url scrolls the url`() = runTrafficTab { _ ->
        val url = urlNode(newestIndex)
        url.performMouseInput {
            moveTo(center)
            repeat(WHEEL_NOTCHES) { scroll(1f, ScrollWheel.Horizontal) }
        }
        waitUntil("the url scrolls right") { url.horizontalScrollRange().value() > 0f }
    }

    @Test
    fun `a vertical wheel over the url scrolls the list and leaves the url alone`() = runTrafficTab { _ ->
        // Aim at a row well down the viewport and send a single notch, so the row the wheel lands on
        // is still composed afterwards and its horizontal offset can be re-read.
        val index = newestIndex - 10
        val list = onNode(hasScrollToIndexAction())
        assertEquals(0f, list.verticalScrollRange().value(), "list starts at the top")

        urlNode(index).performMouseInput {
            moveTo(center)
            scroll(1f, ScrollWheel.Vertical)
        }

        waitUntil("the list scrolls down") { list.verticalScrollRange().value() > 0f }
        assertEquals(0f, urlNode(index).horizontalScrollRange().value(), "the url did not move sideways")
    }

    @Test
    fun `clicking the url selects its row`() = runTrafficTab { selected ->
        urlNode(newestIndex).performClick()
        waitForIdle()
        assertEquals(txId(newestIndex), selected())
    }
}

/** Enough rows that the list can scroll vertically well past the viewport. */
private const val TRANSACTION_COUNT = 60

/** Mouse wheel notches to send; one notch alone is a sub-pixel move under smooth scrolling. */
private const val WHEEL_NOTCHES = 10

/** [TrafficTab] renders newest-first, so the newest transaction owns the topmost row. */
private val newestIndex = TRANSACTION_COUNT - 1

private fun txId(index: Int) = "tx-$index"

/**
 * Long enough to overflow the list pane at any plausible split position, and index-tagged so each
 * row is individually addressable.
 */
private fun url(index: Int) =
    "https://api.example.com/v1/organizations/acme-corp/projects/atlas/deployments/$index" +
        "?environment=production&include=metrics%2Ctraces&page=3"

private fun transaction(index: Int) = HttpTransaction(
    request = CapturedHttpRequest(
        txId = txId(index),
        method = "GET",
        url = url(index),
        timestampMs = index.toLong(),
    ),
    response = CapturedHttpResponse(
        txId = txId(index),
        statusCode = 200,
        statusDescription = "OK",
        durationMs = 12L,
    ),
)

/**
 * Renders [TrafficTab] at a fixed size and runs [block] against it. The lambda receives a getter for
 * the most recently selected transaction id.
 */
@OptIn(ExperimentalTestApi::class)
private fun runTrafficTab(block: ComposeUiTest.(selected: () -> String?) -> Unit) = runComposeUiTest {
    var selected: String? = null
    setContent {
        CompositionLocalProvider(LocalJetWhalePluginStorage provides InMemoryPluginStorage()) {
            MaterialTheme {
                // Wide enough to clear ListMinWidth + DetailMinWidth, tall enough that the list has
                // far more rows than fit.
                Box(Modifier.requiredSize(width = 900.dp, height = 600.dp)) {
                    TrafficTab(
                        transactions = List(TRANSACTION_COUNT) { transaction(it) },
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

private fun SemanticsNodeInteraction.horizontalScrollRange(): ScrollAxisRange =
    fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]

private fun SemanticsNodeInteraction.verticalScrollRange(): ScrollAxisRange =
    fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

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
