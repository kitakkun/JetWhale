package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import com.kitakkun.jetwhale.host.sdk.LocalJetWhalePluginStorage
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ComposeSemanticsInspectorScreenRootTest {
    @Test
    fun `with highlight on the device points at the hovered row and falls back to the selection`() = runComposeUiTest {
        val targets = mutableListOf<NodeKey?>()
        val storage = InMemoryPluginStorage(mapOf("highlight-on-device" to "true"))
        val snapshot = snapshot(root("window", node = node(id = 1, text = "top", children = listOf(node(id = 2, text = "first"), node(id = 3, text = "second")))))
        setContent {
            CompositionLocalProvider(LocalJetWhalePluginStorage provides storage) {
                JwTheme(darkTheme = true) {
                    ComposeSemanticsInspectorScreenRoot(
                        snapshot = snapshot,
                        capturing = false,
                        roundTripMs = null,
                        errorMessage = null,
                        actionStatus = null,
                        highlightStatus = null,
                        viewAttributes = ViewAttributesUiState.Empty,
                        onCapture = {},
                        onPerformAction = {},
                        onSelectedNodeChange = {},
                        onHighlightTargetChange = { targets += it },
                        onCommitViewAttribute = { _, _ -> },
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithText("first").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithText("first").performClick()
        waitForIdle()
        onNodeWithText("second").performMouseInput { moveTo(center) }
        waitForIdle()
        onRoot().performMouseInput { moveTo(Offset(1f, 1f)) }
        waitForIdle()

        val first = NodeKey(rootId = "window", nodeId = 2)
        assertEquals(listOf(null, first, NodeKey(rootId = "window", nodeId = 3), first), targets)
    }
}

/** Plugin storage held in memory, values kept as the JSON the real store would write. */
private class InMemoryPluginStorage(initial: Map<String, String>) : JetWhalePluginStorage {
    private val values = MutableStateFlow(initial)

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
