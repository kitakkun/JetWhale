package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the tree pane says when it has no row to draw. */
internal data class EmptyTreeMessage(val title: String, val description: String?)

/**
 * Everything [ComposeSemanticsInspectorScreen] draws.
 *
 * The screen is a function of this value: it holds no state of its own and runs no effect, so the
 * rows, the summary line and the empty-state wording are all decided in
 * [composeSemanticsInspectorPresenter] and merely rendered here. What the screen wants done goes the
 * other way, as a [ComposeSemanticsInspectorAction].
 */
internal data class ComposeSemanticsInspectorUiState(
    val capturing: Boolean,
    val merged: Boolean,
    val interactiveOnly: Boolean,
    val includeInvisible: Boolean,
    val autoRefresh: Boolean,
    val highlightOnDevice: Boolean,
    val search: String,
    /** The flattened tree, already filtered and collapsed. */
    val rows: List<TreeRow>,
    /** Why [rows] is empty; only read when it is. */
    val emptyMessage: EmptyTreeMessage,
    val selectedKey: NodeKey?,
    /** The selected node as the current snapshot holds it, or `null` when the snapshot has no such node. */
    val selectedNode: UiNode?,
    val statusSummary: String,
    val warnings: List<String>,
    val errorMessage: String?,
    val actionStatus: String?,
    /** Only ever set when the app refused to show the highlight. */
    val highlightStatus: String?,
    val viewAttributes: ViewAttributesUiState,
)

/** Everything [ComposeSemanticsInspectorScreen] asks [composeSemanticsInspectorPresenter] to do. */
internal sealed interface ComposeSemanticsInspectorAction {
    data object Refresh : ComposeSemanticsInspectorAction
    data class ChangeMerged(val merged: Boolean) : ComposeSemanticsInspectorAction
    data class ChangeInteractiveOnly(val interactiveOnly: Boolean) : ComposeSemanticsInspectorAction
    data class ChangeIncludeInvisible(val includeInvisible: Boolean) : ComposeSemanticsInspectorAction
    data class ChangeAutoRefresh(val autoRefresh: Boolean) : ComposeSemanticsInspectorAction
    data class ChangeHighlightOnDevice(val highlightOnDevice: Boolean) : ComposeSemanticsInspectorAction
    data class ChangeSearch(val search: String) : ComposeSemanticsInspectorAction
    data class Select(val key: NodeKey) : ComposeSemanticsInspectorAction
    data class ChangeHover(val key: NodeKey, val hovered: Boolean) : ComposeSemanticsInspectorAction
    data class ToggleExpanded(val key: NodeKey) : ComposeSemanticsInspectorAction
    data class PerformAction(val request: PerformNodeAction) : ComposeSemanticsInspectorAction
    data class CommitViewAttribute(val attribute: ViewAttribute, val value: String) : ComposeSemanticsInspectorAction
}

/**
 * The inspector's own state — how the tree is captured, how it is filtered, and which node is being
 * looked at — and everything derived from it.
 *
 * The screen is left with nothing to remember and no effect to run, which is what makes both the
 * rows and the wording testable without a composition. What stays on the plugin instance stays
 * there for reasons a presenter cannot cover: the attribute store is shared with the MCP tools, so
 * an agent's write lands in the panel too, and the highlight controller has to be able to take the
 * box down when the plugin instance is disposed.
 *
 * @param onCapture suspends until the capture is done, which is what lets auto-refresh pace itself
 *   against the app rather than queue requests it cannot drain.
 * @param onSelectedNodeChange reported rather than acted on: what a selection is worth reading from
 *   the app is the plugin's decision.
 * @param onHighlightTargetChange which node the device should be drawing a box over. Sending it,
 *   holding it against the app's timeout and taking it down again all outlive this composition, so
 *   they are the plugin's.
 */
@Composable
internal fun composeSemanticsInspectorPresenter(
    actions: ReceiveChannel<ComposeSemanticsInspectorAction>,
    snapshot: NodeTreeSnapshot?,
    capturing: Boolean,
    roundTripMs: Long?,
    errorMessage: String?,
    actionStatus: String?,
    viewAttributes: ViewAttributesUiState,
    highlightStatus: String?,
    onCapture: suspend (NodeTreeCaptureOptions) -> Unit,
    onPerformAction: (PerformNodeAction) -> Unit,
    onSelectedNodeChange: (NodeKey?) -> Unit,
    onCommitViewAttribute: (ViewAttribute, String) -> Unit,
    onHighlightTargetChange: (NodeKey?) -> Unit,
): ComposeSemanticsInspectorUiState {
    var merged by rememberPersistent("merged-tree", default = true)
    var interactiveOnly by rememberPersistent("interactive-only", default = false)
    var includeInvisible by rememberPersistent("include-invisible", default = false)
    var autoRefresh by rememberPersistent("auto-refresh", default = false)
    // Off by default, deliberately: the box is drawn into the app itself, so it would otherwise turn
    // up in any `screencap` taken while the inspector is open — a QA run's screenshots included.
    var highlightOnDevice by rememberPersistent("highlight-on-device", default = false)
    var search by remember { mutableStateOf("") }
    var selectedKey by remember { mutableStateOf<NodeKey?>(null) }
    // Whether the pointer is over a row is a fact about the view and nothing outside it reads it;
    // only the highlight target derived from it leaves.
    var hoveredKey by remember { mutableStateOf<NodeKey?>(null) }
    var collapsedKeys by remember { mutableStateOf(emptySet<NodeKey>()) }

    val options = NodeTreeCaptureOptions(merged = merged, includeInvisible = includeInvisible, maxDepth = null)

    // Capture once when the screen opens, and again whenever an option changes what would be
    // captured — an option the user toggled should show its effect without a second click.
    LaunchedEffect(merged, includeInvisible) {
        onCapture(options)
    }
    LaunchedEffect(autoRefresh, merged, includeInvisible) {
        if (!autoRefresh) return@LaunchedEffect
        while (true) {
            delay(AUTO_REFRESH_INTERVAL_MILLIS)
            // Awaited, not fired and forgotten: captures are serialised on the app's main thread,
            // so a fixed-interval loop against a slow app would queue requests faster than they
            // drain and leave the view showing an ever-older tree.
            onCapture(options)
        }
    }

    // Hover wins over selection while the pointer is on a row — "which one is this?" is the question
    // being asked at that moment — and the selection is still there when the pointer leaves.
    val highlighted = if (highlightOnDevice) hoveredKey ?: selectedKey else null
    LaunchedEffect(highlighted) { onHighlightTargetChange(highlighted) }
    DisposableEffect(Unit) {
        onDispose { onHighlightTargetChange(null) }
    }

    val rows = remember(snapshot, search, interactiveOnly, collapsedKeys) {
        buildTreeRows(
            roots = snapshot?.roots.orEmpty(),
            collapsedKeys = collapsedKeys,
            predicate = { node ->
                (!interactiveOnly || node.isInteractive) && node.matchesFreeText(search)
            },
        )
    }
    val selectedNode = selectedKey?.let { key ->
        snapshot?.roots?.firstOrNull { it.rootId == key.rootId }?.findNode(key.nodeId)
    }

    LaunchedEffect(selectedKey) {
        onSelectedNodeChange(selectedKey)
    }

    // The loop outlives any one composition, so it reads the latest callbacks and the options as
    // they stand when an action arrives rather than as they stood when it started.
    val currentOnCapture by rememberUpdatedState(onCapture)
    val currentOnPerformAction by rememberUpdatedState(onPerformAction)
    val currentOnCommitViewAttribute by rememberUpdatedState(onCommitViewAttribute)
    LaunchedEffect(actions) {
        for (action in actions) {
            when (action) {
                // Launched rather than awaited, so a slow capture does not hold up the actions behind it.
                ComposeSemanticsInspectorAction.Refresh -> launch {
                    currentOnCapture(NodeTreeCaptureOptions(merged = merged, includeInvisible = includeInvisible, maxDepth = null))
                }

                is ComposeSemanticsInspectorAction.ChangeMerged -> merged = action.merged

                is ComposeSemanticsInspectorAction.ChangeInteractiveOnly -> interactiveOnly = action.interactiveOnly

                is ComposeSemanticsInspectorAction.ChangeIncludeInvisible -> includeInvisible = action.includeInvisible

                is ComposeSemanticsInspectorAction.ChangeAutoRefresh -> autoRefresh = action.autoRefresh

                is ComposeSemanticsInspectorAction.ChangeHighlightOnDevice -> highlightOnDevice = action.highlightOnDevice

                is ComposeSemanticsInspectorAction.ChangeSearch -> search = action.search

                is ComposeSemanticsInspectorAction.Select -> selectedKey = action.key

                // A row that reports leaving must not clear a hover another row has already taken
                // over — the pointer arrives before the old row lets go.
                is ComposeSemanticsInspectorAction.ChangeHover -> {
                    hoveredKey = if (action.hovered) action.key else hoveredKey.takeIf { it != action.key }
                }

                is ComposeSemanticsInspectorAction.ToggleExpanded -> {
                    collapsedKeys = if (action.key in collapsedKeys) collapsedKeys - action.key else collapsedKeys + action.key
                }

                is ComposeSemanticsInspectorAction.PerformAction -> currentOnPerformAction(action.request)

                is ComposeSemanticsInspectorAction.CommitViewAttribute -> currentOnCommitViewAttribute(action.attribute, action.value)
            }
        }
    }

    return ComposeSemanticsInspectorUiState(
        capturing = capturing,
        merged = merged,
        interactiveOnly = interactiveOnly,
        includeInvisible = includeInvisible,
        autoRefresh = autoRefresh,
        highlightOnDevice = highlightOnDevice,
        search = search,
        rows = rows,
        emptyMessage = emptyTreeMessage(snapshot = snapshot, search = search, interactiveOnly = interactiveOnly),
        selectedKey = selectedKey,
        selectedNode = selectedNode,
        statusSummary = captureSummary(
            snapshot = snapshot,
            rowCount = rows.count { it is TreeRow.NodeRow },
            roundTripMs = roundTripMs,
        ),
        warnings = snapshot?.warnings.orEmpty(),
        errorMessage = errorMessage,
        actionStatus = actionStatus,
        highlightStatus = highlightStatus,
        viewAttributes = viewAttributes,
    )
}

private const val AUTO_REFRESH_INTERVAL_MILLIS = 1_000L

/**
 * The status line's summary of the last capture.
 *
 * Both durations are shown because the point of reading the tree this way rather than through `adb`
 * is the latency, and the two numbers say where any of it went.
 */
internal fun captureSummary(snapshot: NodeTreeSnapshot?, rowCount: Int, roundTripMs: Long?): String = when (snapshot) {
    null -> "Not captured yet."

    else -> buildString {
        append("${snapshot.roots.size} root(s) · $rowCount shown of ${snapshot.nodeCount()} · ")
        append("${snapshot.captureDurationMs} ms on device")
        roundTripMs?.let { append(" · $it ms round trip") }
    }
}

/**
 * Why the tree pane has nothing to draw.
 *
 * A filter that matched nothing is told apart from an app that reported nothing, because the two
 * call for opposite things: relaxing the filter, or installing a probe.
 */
internal fun emptyTreeMessage(
    snapshot: NodeTreeSnapshot?,
    search: String,
    interactiveOnly: Boolean,
): EmptyTreeMessage = when {
    snapshot == null -> EmptyTreeMessage(
        title = "Not captured yet",
        description = "Press Refresh to capture the app's node tree.",
    )

    snapshot.roots.isEmpty() -> EmptyTreeMessage(
        title = "No root reported",
        description = "Install a probe: installJetWhaleSemanticsProbe(application) on Android, installJetWhaleSemanticsProbe() on iOS, or JetWhaleSemanticsProbe() inside your composition.",
    )

    search.isNotBlank() || interactiveOnly -> EmptyTreeMessage(title = "No node matches the current filter", description = null)

    else -> EmptyTreeMessage(title = "The app's roots are empty", description = null)
}
