package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.dragAndDrop
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import com.kitakkun.jetwhale.host.sdk.LocalJetWhalePluginStorage
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NetworkInspectorScreenRootTest {
    @Test
    fun `the traffic table restores its column widths from the plugin's storage and stores a dragged one`() = runComposeUiTest {
        val storage = InMemoryPluginStorage(mapOf("traffic.columnWidths" to """{"URL":200.0}"""))
        setContent {
            CompositionLocalProvider(LocalJetWhalePluginStorage provides storage) {
                JwTheme(darkTheme = false) {
                    Box(Modifier.requiredSize(width = 1200.dp, height = 600.dp)) {
                        NetworkInspectorScreenRoot(
                            transactions = emptyList(),
                            mockRules = emptyList(),
                            mockingEnabled = false,
                            onClearTransactions = {},
                            onToggleMocking = {},
                            onMockRulesChanged = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        assertClose(200.dp, urlColumnWidth())

        val dragPx = with(density) { (-40).dp.toPx() }
        onNodeWithContentDescription("Resize URL").performMouseInput { dragAndDrop(start = center, end = center + Offset(dragPx, 0f)) }
        mainClock.advanceTimeBy(PAST_PERSIST_DEBOUNCE_MILLIS)
        waitForIdle()

        assertClose(160.dp, urlColumnWidth())
        val stored = Json.decodeFromString<Map<String, Float>>(checkNotNull(storage.values.value["traffic.columnWidths"]))
        assertEquals(setOf("URL"), stored.keys)
        assertClose(160.dp, stored.getValue("URL").dp)
    }

    /** The URL column ends where its resize handle ends, and starts one gap after the method column's handle. */
    private fun ComposeUiTest.urlColumnWidth(): Dp {
        val urlEnd = onNodeWithContentDescription("Resize URL").getBoundsInRoot().right
        val methodEnd = onNodeWithContentDescription("Resize Method").getBoundsInRoot().right
        return urlEnd - methodEnd - JwSpacing.medium
    }

    private fun assertClose(expected: Dp, actual: Dp) {
        assertTrue(actual in (expected - 2.dp)..(expected + 2.dp), "expected about $expected, was $actual")
    }
}

/** Longer than `rememberPersistent` waits after the last change before it stores a value. */
private const val PAST_PERSIST_DEBOUNCE_MILLIS = 1_000L

/** Plugin storage held in memory, values kept as the JSON the real store would write. */
private class InMemoryPluginStorage(initial: Map<String, String>) : JetWhalePluginStorage {
    val values = MutableStateFlow(initial)

    override suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>) {
        values.update { it + (key to Json.encodeToString(serializer, value)) }
    }

    override suspend fun <T> get(key: String, serializer: KSerializer<T>): T? = values.value[key]?.let { Json.decodeFromString(serializer, it) }

    override fun <T> getFlow(key: String, serializer: KSerializer<T>): Flow<T?> = values.map { stored -> stored[key]?.let { Json.decodeFromString(serializer, it) } }

    override suspend fun contains(key: String): Boolean = key in values.value

    override suspend fun remove(key: String) {
        values.update { it - key }
    }

    override suspend fun clear() {
        values.value = emptyMap()
    }

    override val keysFlow: Flow<Set<String>> = values.map { it.keys }
}
