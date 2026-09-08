package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import com.kitakkun.jetwhale.host.sdk.LocalJetWhalePluginStorage
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The inspector's own state, tested where it now lives: in the presenter rather than in the view.
 *
 * The five toolbar toggles are persisted, so the keys they are stored under are part of the
 * plugin's on-disk data — renaming one would silently reset that toggle for everyone who had set
 * it. They are asserted here as literals for that reason.
 */
@OptIn(ExperimentalTestApi::class)
class ComposeSemanticsInspectorPresenterTest {

    // -- the tree ---------------------------------------------------------------

    @Test
    fun `collapsing a node hides its children, and expanding it brings them back`() = runPresenter(snapshot = twoLevelTree) { state ->
        assertEquals(listOf(1, 2, 3), state().nodeIds())

        state().onToggleExpanded(NodeKey(ROOT_ID, 1))
        waitForIdle()
        assertEquals(listOf(1), state().nodeIds())

        state().onToggleExpanded(NodeKey(ROOT_ID, 1))
        waitForIdle()
        assertEquals(listOf(1, 2, 3), state().nodeIds())
    }

    @Test
    fun `the search box keeps the matching nodes and the ancestors that hold them`() = runPresenter(snapshot = twoLevelTree) { state ->
        state().onSearchChange("Cancel")
        waitForIdle()

        assertEquals(listOf(1, 3), state().nodeIds())
    }

    @Test
    fun `showing only the interactive nodes drops the rest`() = runPresenter(snapshot = twoLevelTree) { state ->
        state().onInteractiveOnlyChange(true)
        waitForIdle()

        // Node 1 is kept although it is not interactive itself: dropping an ancestor would reparent
        // the match and lose the structure that makes the tree readable.
        assertEquals(listOf(1, 2), state().nodeIds())
        assertTrue(state().interactiveOnly)
    }

    // -- selection --------------------------------------------------------------

    @Test
    fun `selecting a node reports it upward and resolves it against the snapshot`() {
        val selected = mutableListOf<NodeKey?>()
        runPresenter(snapshot = twoLevelTree, onSelectedNodeChange = { selected += it }) { state ->
            state().onSelect(NodeKey(ROOT_ID, 3))
            waitForIdle()

            assertEquals(NodeKey(ROOT_ID, 3), state().selectedKey)
            assertEquals(3, state().selectedNode?.id)
            assertEquals(listOf(null, NodeKey(ROOT_ID, 3)), selected)
        }
    }

    @Test
    fun `nothing is selected to begin with`() = runPresenter(snapshot = twoLevelTree) { state ->
        assertNull(state().selectedKey)
        assertNull(state().selectedNode)
    }

    // -- captures ---------------------------------------------------------------

    @Test
    fun `the screen captures once when it opens`() {
        val captures = mutableListOf<NodeTreeCaptureOptions>()
        runPresenter(onCapture = { captures += it }) { _ ->
            waitForIdle()
            assertEquals(listOf(NodeTreeCaptureOptions(merged = true, includeInvisible = false, maxDepth = null)), captures)
        }
    }

    @Test
    fun `a toggle that changes what would be captured captures again`() {
        val captures = mutableListOf<NodeTreeCaptureOptions>()
        runPresenter(onCapture = { captures += it }) { state ->
            waitForIdle()

            state().onIncludeInvisibleChange(true)
            waitUntil { captures.size == 2 }

            assertEquals(NodeTreeCaptureOptions(merged = true, includeInvisible = true, maxDepth = null), captures.last())
        }
    }

    @Test
    fun `a toggle that only filters the tree captures nothing`() {
        val captures = mutableListOf<NodeTreeCaptureOptions>()
        runPresenter(snapshot = twoLevelTree, onCapture = { captures += it }) { state ->
            waitForIdle()

            state().onInteractiveOnlyChange(true)
            state().onSearchChange("Cancel")
            waitForIdle()

            assertEquals(1, captures.size)
        }
    }

    // -- persisted toggles ------------------------------------------------------

    @Test
    fun `the toggles are stored under the keys they have always used`() {
        val storage = InMemoryPluginStorage()
        runPresenter(storage = storage) { state ->
            state().onMergedChange(false)
            state().onInteractiveOnlyChange(true)
            state().onIncludeInvisibleChange(true)
            state().onAutoRefreshChange(true)
            state().onHighlightOnDeviceChange(true)

            waitUntil { storage.peek("highlight-on-device") == true }
            assertEquals(false, storage.peek("merged-tree"))
            assertEquals(true, storage.peek("interactive-only"))
            assertEquals(true, storage.peek("include-invisible"))
            assertEquals(true, storage.peek("auto-refresh"))
        }
    }

    @Test
    fun `a stored toggle is what the inspector opens with`() {
        val storage = InMemoryPluginStorage(
            "merged-tree" to false,
            "interactive-only" to true,
            "include-invisible" to true,
            "auto-refresh" to true,
            "highlight-on-device" to true,
        )
        runPresenter(storage = storage) { state ->
            waitUntil { !state().merged }

            assertTrue(state().interactiveOnly)
            assertTrue(state().includeInvisible)
            assertTrue(state().autoRefresh)
            assertTrue(state().highlightOnDevice)
        }
    }

    @Test
    fun `an inspector opened for the first time starts on the defaults`() = runPresenter { state ->
        assertTrue(state().merged)
        assertEquals(false, state().interactiveOnly)
        assertEquals(false, state().includeInvisible)
        assertEquals(false, state().autoRefresh)
        // Off deliberately: the box is drawn into the app, so it would otherwise turn up in any
        // screenshot taken while the inspector is open.
        assertEquals(false, state().highlightOnDevice)
    }

    // -- the highlight ----------------------------------------------------------

    @Test
    fun `the device is pointed at the hovered row, and at nothing once the toggle is off`() {
        val targets = mutableListOf<NodeKey?>()
        runPresenter(snapshot = twoLevelTree, onHighlightTargetChange = { targets += it }) { state ->
            state().onHighlightOnDeviceChange(true)
            state().onHoverChange(NodeKey(ROOT_ID, 2), true)
            waitUntil { targets.lastOrNull() == NodeKey(ROOT_ID, 2) }

            state().onHighlightOnDeviceChange(false)
            waitUntil { targets.lastOrNull() == null }
        }
    }

    @Test
    fun `a row that reports leaving does not clear a hover another row has taken over`() {
        val targets = mutableListOf<NodeKey?>()
        runPresenter(snapshot = twoLevelTree, onHighlightTargetChange = { targets += it }) { state ->
            state().onHighlightOnDeviceChange(true)
            state().onHoverChange(NodeKey(ROOT_ID, 2), true)
            waitUntil { targets.lastOrNull() == NodeKey(ROOT_ID, 2) }

            // The pointer arrives on the next row before the one it left reports leaving.
            state().onHoverChange(NodeKey(ROOT_ID, 3), true)
            state().onHoverChange(NodeKey(ROOT_ID, 2), false)
            waitForIdle()

            assertEquals(NodeKey(ROOT_ID, 3), targets.last())
        }
    }
}

/** The status line and the empty pane, which the presenter decides and the view merely draws. */
class InspectorWordingTest {
    @Test
    fun `a tree that has not been captured says so`() {
        assertEquals("Not captured yet.", captureSummary(snapshot = null, rowCount = 0, roundTripMs = null))
    }

    @Test
    fun `the summary counts the shown rows against the captured ones`() {
        assertEquals(
            "1 root(s) · 2 shown of 3 · 1 ms on device",
            captureSummary(snapshot = twoLevelTree, rowCount = 2, roundTripMs = null),
        )
    }

    @Test
    fun `a round trip is reported beside the time spent on the device`() {
        assertEquals(
            "1 root(s) · 3 shown of 3 · 1 ms on device · 7 ms round trip",
            captureSummary(snapshot = twoLevelTree, rowCount = 3, roundTripMs = 7),
        )
    }

    @Test
    fun `an empty pane before the first capture points at the Refresh button`() {
        val message = emptyTreeMessage(snapshot = null, search = "", interactiveOnly = false)
        assertEquals("Not captured yet", message.title)
        assertEquals("Press Refresh to capture the app's node tree.", message.description)
    }

    @Test
    fun `an app that reported no root is told to install a probe`() {
        val message = emptyTreeMessage(snapshot = snapshot(), search = "", interactiveOnly = false)
        assertEquals("No Compose root reported", message.title)
        assertTrue(message.description.orEmpty().contains("installJetWhaleSemanticsProbe"))
    }

    @Test
    fun `a filter that matched nothing is told apart from an app with nothing to show`() {
        assertEquals(
            "No node matches the current filter",
            emptyTreeMessage(snapshot = twoLevelTree, search = "nothing here", interactiveOnly = false).title,
        )
        assertEquals(
            "No node matches the current filter",
            emptyTreeMessage(snapshot = twoLevelTree, search = "", interactiveOnly = true).title,
        )
        assertEquals(
            "The app's Compose roots are empty",
            emptyTreeMessage(snapshot = twoLevelTree, search = "", interactiveOnly = false).title,
        )
    }
}

private const val ROOT_ID = "window-1"

/**
 * A root holding a plain container and, under it, one node that can be operated and one that cannot,
 * so a collapse has something to hide and each filter has something to drop.
 */
private val twoLevelTree: NodeTreeSnapshot = snapshot(
    root(
        rootId = ROOT_ID,
        node = node(
            id = 1,
            children = listOf(
                node(id = 2, role = "Button", text = "Continue", actions = listOf("OnClick"), isClickable = true),
                node(id = 3, text = "Cancel"),
            ),
        ),
    ),
)

private fun ComposeSemanticsInspectorUiState.nodeIds(): List<Int> = rows.filterIsInstance<TreeRow.NodeRow>().map { it.node.id }

/**
 * Composes the presenter and runs [block] against the state it returns.
 *
 * The state is handed over as a getter rather than a value: every intent the test fires produces a
 * new one, and a captured value would go stale the moment the test used it.
 */
@OptIn(ExperimentalTestApi::class)
private fun runPresenter(
    snapshot: NodeTreeSnapshot? = null,
    storage: JetWhalePluginStorage = InMemoryPluginStorage(),
    onCapture: (NodeTreeCaptureOptions) -> Unit = {},
    onSelectedNodeChange: (NodeKey?) -> Unit = {},
    onHighlightTargetChange: (NodeKey?) -> Unit = {},
    block: suspend ComposeUiTest.(state: () -> ComposeSemanticsInspectorUiState) -> Unit,
) = runComposeUiTest {
    lateinit var uiState: ComposeSemanticsInspectorUiState
    setContent {
        CompositionLocalProvider(LocalJetWhalePluginStorage provides storage) {
            uiState = composeSemanticsInspectorPresenter(
                snapshot = snapshot,
                capturing = false,
                roundTripMs = null,
                errorMessage = null,
                actionStatus = null,
                viewAttributes = ViewAttributesUiState.Empty,
                highlightStatus = null,
                onCapture = { options -> onCapture(options) },
                onPerformAction = {},
                onSelectedNodeChange = onSelectedNodeChange,
                onCommitViewAttribute = { _, _ -> },
                onHighlightTargetChange = onHighlightTargetChange,
            )
        }
    }
    block { uiState }
}

/** Minimal in-memory [JetWhalePluginStorage] so `rememberPersistent` has something to bind to. */
@Suppress("UNCHECKED_CAST")
private class InMemoryPluginStorage(vararg initial: Pair<String, Any?>) : JetWhalePluginStorage {
    private val values = MutableStateFlow(initial.toMap())

    /** Reads without suspending, so a `waitUntil` condition can watch a write land. */
    fun peek(key: String): Any? = values.value[key]

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
