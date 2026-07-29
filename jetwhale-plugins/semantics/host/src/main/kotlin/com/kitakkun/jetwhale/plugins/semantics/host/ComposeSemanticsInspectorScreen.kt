package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Browser for the Compose node tree of the running app: the tree on the left, the selected node's
 * semantics and the actions it exposes on the right.
 *
 * Captures are pull-based — a capture reads the debuggee's semantics on its main thread, so the app
 * pays only when someone is looking. Auto-refresh exists for watching a screen change, but is off by
 * default for the same reason.
 */
@Composable
internal fun ComposeSemanticsInspectorScreen(
    snapshot: NodeTreeSnapshot?,
    capturing: Boolean,
    roundTripMs: Long?,
    errorMessage: String?,
    actionStatus: String?,
    onCapture: suspend (NodeTreeCaptureOptions) -> Unit,
    onPerformAction: (PerformNodeAction) -> Unit,
) {
    var merged by rememberPersistent("merged-tree", default = true)
    var interactiveOnly by rememberPersistent("interactive-only", default = false)
    var includeInvisible by rememberPersistent("include-invisible", default = false)
    var autoRefresh by rememberPersistent("auto-refresh", default = false)
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var selectedKey by remember { mutableStateOf<NodeKey?>(null) }
    val collapsedKeys = remember { mutableStateMapOf<NodeKey, Unit>() }

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

    val rows = remember(snapshot, search, interactiveOnly, collapsedKeys.keys.toSet()) {
        buildTreeRows(
            roots = snapshot?.roots.orEmpty(),
            collapsedKeys = collapsedKeys.keys.toSet(),
            predicate = { node ->
                (!interactiveOnly || node.isInteractive) && node.matchesFreeText(search)
            },
        )
    }
    val selectedNode = selectedKey?.let { key ->
        snapshot?.roots?.firstOrNull { it.rootId == key.rootId }?.findNode(key.nodeId)
    }

    // The host hands the plugin an unpainted scene, so the screen paints its own background;
    // without it the areas no child covers fall back to white and fight a dark theme.
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Toolbar(
            capturing = capturing,
            merged = merged,
            interactiveOnly = interactiveOnly,
            includeInvisible = includeInvisible,
            autoRefresh = autoRefresh,
            search = search,
            onRefresh = { scope.launch { onCapture(options) } },
            onMergedChange = { merged = it },
            onInteractiveOnlyChange = { interactiveOnly = it },
            onIncludeInvisibleChange = { includeInvisible = it },
            onAutoRefreshChange = { autoRefresh = it },
            onSearchChange = { search = it },
        )
        StatusLine(
            snapshot = snapshot,
            rowCount = rows.count { it is TreeRow.NodeRow },
            roundTripMs = roundTripMs,
            errorMessage = errorMessage,
            actionStatus = actionStatus,
        )
        HorizontalDivider()
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.58f).fillMaxHeight()) {
                if (rows.isEmpty()) {
                    EmptyTreeMessage(snapshot = snapshot, search = search, interactiveOnly = interactiveOnly)
                } else {
                    TreeList(
                        rows = rows,
                        selectedKey = selectedKey,
                        onSelect = { selectedKey = it },
                        onToggleExpanded = { key ->
                            if (collapsedKeys.remove(key) == null) collapsedKeys[key] = Unit
                        },
                    )
                }
            }
            VerticalDivider()
            Box(Modifier.weight(0.42f).fillMaxHeight()) {
                NodeDetail(
                    rootId = selectedKey?.rootId,
                    node = selectedNode,
                    onPerformAction = onPerformAction,
                )
            }
        }
    }
}

private const val AUTO_REFRESH_INTERVAL_MILLIS = 1_000L

@Composable
private fun Toolbar(
    capturing: Boolean,
    merged: Boolean,
    interactiveOnly: Boolean,
    includeInvisible: Boolean,
    autoRefresh: Boolean,
    search: String,
    onRefresh: () -> Unit,
    onMergedChange: (Boolean) -> Unit,
    onInteractiveOnlyChange: (Boolean) -> Unit,
    onIncludeInvisibleChange: (Boolean) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(onClick = onRefresh, enabled = !capturing) {
            Text(if (capturing) "Capturing…" else "Refresh")
        }
        LabelledCheckbox("Auto", autoRefresh, onAutoRefreshChange)
        LabelledCheckbox("Merged", merged, onMergedChange)
        LabelledCheckbox("Interactive only", interactiveOnly, onInteractiveOnlyChange)
        LabelledCheckbox("Include invisible", includeInvisible, onIncludeInvisibleChange)
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text("Search text / tag / role") },
            singleLine = true,
            modifier = Modifier.width(260.dp),
        )
    }
}

@Composable
private fun LabelledCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onCheckedChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusLine(
    snapshot: NodeTreeSnapshot?,
    rowCount: Int,
    roundTripMs: Long?,
    errorMessage: String?,
    actionStatus: String?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val summary = when (snapshot) {
            null -> "Not captured yet."

            else -> buildString {
                append("${snapshot.roots.size} root(s) · $rowCount shown of ${snapshot.nodeCount()} · ")
                append("${snapshot.captureDurationMs} ms on device")
                roundTripMs?.let { append(" · $it ms round trip") }
            }
        }
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        snapshot?.warnings?.forEach { warning ->
            Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        errorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        actionStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun EmptyTreeMessage(snapshot: NodeTreeSnapshot?, search: String, interactiveOnly: Boolean) {
    val message = when {
        snapshot == null -> "Press Refresh to capture the app's node tree."
        snapshot.roots.isEmpty() -> "The app reported no Compose root. Install a probe: installJetWhaleSemanticsProbe(application), or call JetWhaleSemanticsProbe() inside your composition."
        search.isNotBlank() || interactiveOnly -> "No node matches the current filter."
        else -> "The app's Compose roots are empty."
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TreeList(
    rows: List<TreeRow>,
    selectedKey: NodeKey?,
    onSelect: (NodeKey) -> Unit,
    onToggleExpanded: (NodeKey) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is TreeRow.RootHeader -> RootHeaderRow(row)

                is TreeRow.NodeRow -> NodeRow(
                    row = row,
                    selected = selectedKey == NodeKey(row.rootId, row.node.id),
                    onSelect = { onSelect(NodeKey(row.rootId, row.node.id)) },
                    onToggleExpanded = { onToggleExpanded(NodeKey(row.rootId, row.node.id)) },
                )
            }
        }
    }
}

@Composable
private fun RootHeaderRow(row: TreeRow.RootHeader) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.root.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${row.nodeCount} nodes · ×${row.root.density}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NodeRow(
    row: TreeRow.NodeRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val label = row.node.displayLabel()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onSelect)
            .padding(start = (8 + row.depth * 14).dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A fixed-width slot even for leaves, so labels at the same depth line up rather than
        // shifting by whether a node happens to have children.
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
            if (row.expandable) {
                Text(
                    text = if (row.expanded) "▾" else "▸",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable(onClick = onToggleExpanded),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (row.node.isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (row.node.isInteractive) {
            Text(
                text = row.node.actionSummary(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        // A node with no semantics of its own is already labelled by its id; repeating it here
        // would render "#12 #12".
        if (!label.startsWith("#")) {
            Text(
                text = "#${row.node.id}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun ComposeNode.actionSummary(): String = when {
    isClickable -> "clickable"
    isEditable -> "editable"
    isScrollable -> "scrollable"
    else -> actions.firstOrNull() ?: ""
}

@Composable
private fun NodeDetail(
    rootId: String?,
    node: ComposeNode?,
    onPerformAction: (PerformNodeAction) -> Unit,
) {
    if (node == null || rootId == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Select a node to see its semantics and the actions it exposes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val clipboard = LocalClipboardManager.current
    var textInput by remember(node.id) { mutableStateOf(node.editableText ?: "") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(node.displayLabel(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        PropertyRow("id", node.id.toString())
        PropertyRow("rootId", rootId)
        node.role?.let { PropertyRow("role", it) }
        node.text?.let { PropertyRow("text", it, wrap = true) }
        node.editableText?.let { PropertyRow("editableText", it, wrap = true) }
        node.contentDescription?.let { PropertyRow("contentDescription", it, wrap = true) }
        node.testTag?.let { PropertyRow("testTag", it) }
        node.stateDescription?.let { PropertyRow("stateDescription", it, wrap = true) }
        node.toggleableState?.let { PropertyRow("toggleableState", it) }
        PropertyRow("bounds (root)", node.bounds.formatted())
        PropertyRow("bounds (screen)", node.boundsInScreen.formatted())
        PropertyRow(
            "flags",
            buildList {
                if (!node.isEnabled) add("disabled")
                if (!node.isVisible) add("invisible")
                if (node.isFocused) add("focused")
                if (node.isSelected) add("selected")
                if (node.isClickable) add("clickable")
                if (node.isEditable) add("editable")
                if (node.isScrollable) add("scrollable")
            }.joinToString(", ").ifEmpty { "—" },
            wrap = true,
        )
        PropertyRow("actions", node.actions.joinToString(", ").ifEmpty { "—" }, wrap = true)

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Run an action on the app", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionButton("Click", node, NodeAction.Click, rootId, onPerformAction)
            ActionButton("Long click", node, NodeAction.LongClick, rootId, onPerformAction)
            ActionButton("Focus", node, NodeAction.RequestFocus, rootId, onPerformAction)
            ActionButton("Dismiss", node, NodeAction.Dismiss, rootId, onPerformAction)
            ActionButton("Expand", node, NodeAction.Expand, rootId, onPerformAction)
            ActionButton("Collapse", node, NodeAction.Collapse, rootId, onPerformAction)
        }

        if (node.actions.contains("SetText")) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Text") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.SetText, text = textInput))
                    },
                ) {
                    Text("Set text")
                }
            }
        }

        if (node.isScrollable) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollBy, scrollY = SCROLL_STEP_PX))
                    },
                ) {
                    Text("Scroll down")
                }
                OutlinedButton(
                    onClick = {
                        onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollBy, scrollY = -SCROLL_STEP_PX))
                    },
                ) {
                    Text("Scroll up")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(node.adbTapCommand()))
            },
            enabled = !node.boundsInScreen.isEmpty,
        ) {
            Text("Copy `adb shell input tap` for these bounds")
        }
    }
}

private const val SCROLL_STEP_PX = 400f

private fun ComposeNode.adbTapCommand(): String = "adb shell input tap ${boundsInScreen.centerX.roundToInt()} ${boundsInScreen.centerY.roundToInt()}"

@Composable
private fun ActionButton(
    label: String,
    node: ComposeNode,
    action: NodeAction,
    rootId: String,
    onPerformAction: (PerformNodeAction) -> Unit,
) {
    // Only offer what the node actually advertises: a button for an action the node does not expose
    // would always come back "not exposed", which is noise rather than feedback.
    val exposed = node.actions.contains(action.semanticsKeyName)
    if (!exposed) return
    OutlinedButton(
        onClick = { onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = action)) },
        enabled = node.isEnabled,
    ) {
        Text(label)
    }
}

/** The semantics key an action arrives under in [ComposeNode.actions]. */
private val NodeAction.semanticsKeyName: String
    get() = when (this) {
        NodeAction.Click -> "OnClick"
        NodeAction.LongClick -> "OnLongClick"
        NodeAction.SetText -> "SetText"
        NodeAction.InsertText -> "InsertTextAtCursor"
        NodeAction.ImeAction -> "PerformImeAction"
        NodeAction.ScrollBy -> "ScrollBy"
        NodeAction.RequestFocus -> "RequestFocus"
        NodeAction.Dismiss -> "Dismiss"
        NodeAction.Expand -> "Expand"
        NodeAction.Collapse -> "Collapse"
    }

/**
 * @param wrap `true` for values worth reading in full at a glance — the action list, the app's own
 *   strings. The rest stay on one scrollable line so the columns line up and a long id cannot push
 *   the properties below it off the pane.
 */
@Composable
private fun PropertyRow(label: String, value: String, wrap: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        if (wrap) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
