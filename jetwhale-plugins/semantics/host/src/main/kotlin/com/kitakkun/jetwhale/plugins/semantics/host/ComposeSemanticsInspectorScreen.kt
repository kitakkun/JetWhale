package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCheckbox
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwProgressIndicator
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwStatusLine
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTreeRow
import com.kitakkun.jetwhale.host.ui.LocalJwContentColor
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.advertisedAs
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt

/** The capture, filter and highlight toggles the toolbar offers, each persisted under its own storage key. */
internal data class SemanticsInspectorFlags(
    val merged: Boolean,
    val interactiveOnly: Boolean,
    val includeInvisible: Boolean,
    val autoRefresh: Boolean,
    val highlightOnDevice: Boolean,
)

@Composable
internal fun ComposeSemanticsInspectorScreen(
    snapshot: NodeTreeSnapshot?,
    capturing: Boolean,
    roundTripMs: Long?,
    errorMessage: String?,
    actionStatus: String?,
    highlightStatus: String?,
    viewAttributes: ViewAttributesUiState,
    flags: SemanticsInspectorFlags,
    selectedKey: NodeKey?,
    onRefresh: () -> Unit,
    onFlagsChange: (SemanticsInspectorFlags) -> Unit,
    onSelectKey: (NodeKey) -> Unit,
    onHoverChange: (NodeKey, Boolean) -> Unit,
    onPerformAction: (PerformNodeAction) -> Unit,
    onCommitViewAttribute: (ViewAttribute, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    val collapsedKeys = remember { mutableStateMapOf<NodeKey, Unit>() }

    val rows = remember(snapshot, search, flags.interactiveOnly, collapsedKeys.keys.toSet()) {
        buildTreeRows(
            roots = snapshot?.roots.orEmpty(),
            collapsedKeys = collapsedKeys.keys.toSet(),
            predicate = { node ->
                (!flags.interactiveOnly || node.isInteractive) && node.matchesFreeText(search)
            },
        )
    }
    val selectedNode = selectedKey?.let { key ->
        snapshot?.roots?.firstOrNull { it.rootId == key.rootId }?.findNode(key.nodeId)
    }

    // The host hands the plugin an unpainted scene, so the screen paints its own background;
    // without it the areas no child covers fall back to white and fight a dark theme.
    Column(modifier.fillMaxSize().background(JwTheme.colors.surface)) {
        Toolbar(
            capturing = capturing,
            flags = flags,
            search = search,
            onRefresh = onRefresh,
            onFlagsChange = onFlagsChange,
            onSearchChange = { search = it },
        )
        StatusLine(
            snapshot = snapshot,
            rowCount = rows.count { it is TreeRow.NodeRow },
            roundTripMs = roundTripMs,
            errorMessage = errorMessage,
            actionStatus = actionStatus,
            highlightStatus = highlightStatus,
        )
        JwHorizontalDivider()
        JwSplitPane(
            modifier = Modifier.fillMaxSize(),
            state = rememberJwSplitPaneState(TREE_PANE_FRACTION),
            first = {
                if (rows.isEmpty()) {
                    EmptyTreeMessage(snapshot = snapshot, search = search, interactiveOnly = flags.interactiveOnly)
                } else {
                    TreeList(
                        rows = rows,
                        selectedKey = selectedKey,
                        onSelect = onSelectKey,
                        onHoverChange = onHoverChange,
                        onToggleExpanded = { key ->
                            if (collapsedKeys.remove(key) == null) collapsedKeys[key] = Unit
                        },
                    )
                }
            },
            second = {
                NodeDetail(
                    rootId = selectedKey?.rootId,
                    node = selectedNode,
                    viewAttributes = viewAttributes,
                    onPerformAction = onPerformAction,
                    onCommitViewAttribute = onCommitViewAttribute,
                )
            },
        )
    }
}

/** The tree gets a little more than half; node labels are longer than property rows. */
private const val TREE_PANE_FRACTION = 0.58f

/** Wide enough for the placeholder without crowding the checkboxes beside it. */
private val SearchFieldWidth = 260.dp

/** Fits "contentDescription", the longest property name. */
private val PropertyKeyWidth = 120.dp

@Composable
private fun Toolbar(
    capturing: Boolean,
    flags: SemanticsInspectorFlags,
    search: String,
    onRefresh: () -> Unit,
    onFlagsChange: (SemanticsInspectorFlags) -> Unit,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = JwSpacing.medium, vertical = JwSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
    ) {
        JwButton(
            onClick = onRefresh,
            enabled = !capturing,
            style = JwButtonStyle.Primary,
        ) {
            if (capturing) {
                JwProgressIndicator()
            }
            JwText(if (capturing) "Capturing…" else "Refresh")
        }
        JwCheckbox(
            checked = flags.autoRefresh,
            onCheckedChange = { onFlagsChange(flags.copy(autoRefresh = it)) },
            label = "Auto",
        )
        JwCheckbox(
            checked = flags.merged,
            onCheckedChange = { onFlagsChange(flags.copy(merged = it)) },
            label = "Merged",
        )
        JwCheckbox(
            checked = flags.interactiveOnly,
            onCheckedChange = { onFlagsChange(flags.copy(interactiveOnly = it)) },
            label = "Interactive only",
        )
        JwCheckbox(
            checked = flags.includeInvisible,
            onCheckedChange = { onFlagsChange(flags.copy(includeInvisible = it)) },
            label = "Include invisible",
        )
        JwCheckbox(
            checked = flags.highlightOnDevice,
            onCheckedChange = { onFlagsChange(flags.copy(highlightOnDevice = it)) },
            label = "Highlight",
        )
        JwSearchField(
            value = search,
            onValueChange = onSearchChange,
            clearLabel = "Clear search",
            placeholder = "Search text / tag / role",
            modifier = Modifier.width(SearchFieldWidth),
        )
    }
}

@Composable
private fun StatusLine(
    snapshot: NodeTreeSnapshot?,
    rowCount: Int,
    roundTripMs: Long?,
    errorMessage: String?,
    actionStatus: String?,
    highlightStatus: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val summary = when (snapshot) {
            null -> "Not captured yet."

            else -> buildString {
                append("${snapshot.roots.size} root(s) · $rowCount shown of ${snapshot.nodeCount()} · ")
                append("${snapshot.captureDurationMs} ms on device")
                roundTripMs?.let { append(" · $it ms round trip") }
            }
        }
        JwStatusLine(text = summary)
        snapshot?.warnings?.forEach { warning -> JwStatusLine(text = warning, tone = JwTone.Warning) }
        errorMessage?.let { JwStatusLine(text = it, tone = JwTone.Error) }
        actionStatus?.let { JwStatusLine(text = it, tone = JwTone.Accent) }
        // Only ever set when the app refused to show the highlight; a highlight that is up says so
        // by being on the device.
        highlightStatus?.let { JwStatusLine(text = "Highlight: $it", tone = JwTone.Warning) }
    }
}

@Composable
private fun EmptyTreeMessage(snapshot: NodeTreeSnapshot?, search: String, interactiveOnly: Boolean) {
    val (title, description) = when {
        snapshot == null -> "Not captured yet" to "Press Refresh to capture the app's node tree."
        snapshot.roots.isEmpty() -> "No root reported" to "Install a probe: installJetWhaleSemanticsProbe(application) on Android, installJetWhaleSemanticsProbe() on iOS, or JetWhaleSemanticsProbe() inside your composition."
        search.isNotBlank() || interactiveOnly -> "No node matches the current filter" to null
        else -> "The app's roots are empty" to null
    }
    JwEmptyState(title = title, description = description)
}

@Composable
private fun TreeList(
    rows: List<TreeRow>,
    selectedKey: NodeKey?,
    onSelect: (NodeKey) -> Unit,
    onHoverChange: (NodeKey, Boolean) -> Unit,
    onToggleExpanded: (NodeKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize()) {
        items(rows, key = TreeRow::key) { row ->
            when (row) {
                is TreeRow.RootHeader -> RootHeaderRow(row)

                is TreeRow.NodeRow -> NodeRow(
                    row = row,
                    selected = selectedKey == NodeKey(row.rootId, row.node.id),
                    onSelect = { onSelect(NodeKey(row.rootId, row.node.id)) },
                    onHoverChange = { hovered -> onHoverChange(NodeKey(row.rootId, row.node.id), hovered) },
                    onToggleExpanded = { onToggleExpanded(NodeKey(row.rootId, row.node.id)) },
                )
            }
        }
    }
}

@Composable
private fun RootHeaderRow(row: TreeRow.RootHeader) {
    JwSectionHeader(
        title = row.root.label,
        trailing = {
            JwText(
                text = "${row.nodeCount} nodes · ×${row.root.density}",
                style = JwTheme.textStyles.labelSmall,
                color = JwTheme.colors.textSecondary,
            )
        },
        modifier = Modifier.background(JwTheme.colors.sidebarBackground),
    )
}

@Composable
private fun NodeRow(
    row: TreeRow.NodeRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onHoverChange: (Boolean) -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = row.node.displayLabel()
    // JwTreeRow tracks hover for its own tint through an interaction source it keeps to itself, so
    // the row is made hoverable a second time here rather than the component growing a callback.
    val hoverInteractionSource = remember(calculation = ::MutableInteractionSource)
    val hovered by hoverInteractionSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) { onHoverChange(hovered) }
    // A row filtered or collapsed away under a resting pointer never reports leaving on its own, and
    // the box would stay on a node the pointer is no longer over.
    DisposableEffect(Unit) {
        onDispose { onHoverChange(false) }
    }
    JwTreeRow(
        modifier = modifier.hoverable(hoverInteractionSource),
        text = label,
        depth = row.depth,
        expandable = row.expandable,
        expanded = row.expanded,
        selected = selected,
        // An invisible node is still selectable and expandable; it is only drawn muted.
        muted = !row.node.isVisible,
        onClick = onSelect,
        onToggleExpanded = onToggleExpanded,
        trailingContent = {
            // The node types interleave in one tree, and which one a row is decides how to read it —
            // so the non-Compose nodes are tagged rather than left to be inferred from the label.
            when (row.node) {
                is ViewNode -> JwTag(text = "View", tone = JwTone.Info)
                is AppleNode -> JwTag(text = "iOS", tone = JwTone.Info)
                is ComposeNode -> Unit
            }
            if (row.node.isInteractive) {
                JwTag(text = row.node.actionSummary(), tone = JwTone.Accent)
            }
            // A node that offers something to do but cannot be operated is the one worth spotting
            // from the tree, without opening it.
            if (row.node.isInteractive && !row.node.isOperable) {
                JwTag(text = "not operable", tone = JwTone.Warning)
            }
            // A node with no semantics of its own is already labelled by its id; repeating it here
            // would render "#12 #12".
            if (!label.startsWith("#")) {
                JwText(
                    text = "#${row.node.id}",
                    style = JwTheme.textStyles.labelSmall,
                    color = JwTheme.colors.textSecondary,
                )
            }
        },
    )
}

private fun UiNode.actionSummary(): String = when {
    isClickable -> "clickable"
    isEditable -> "editable"
    isScrollable -> "scrollable"
    else -> actions.firstOrNull() ?: ""
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NodeDetail(
    rootId: String?,
    node: UiNode?,
    viewAttributes: ViewAttributesUiState,
    onPerformAction: (PerformNodeAction) -> Unit,
    onCommitViewAttribute: (ViewAttribute, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (node == null || rootId == null) {
        JwEmptyState(title = "Select a node to see its semantics and the actions it exposes.")
        return
    }

    val scope = rememberCoroutineScope()
    // Ids are per root, so the same id in another root is another node.
    var textInput by remember(rootId, node.id) { mutableStateOf(node.editableText ?: "") }
    var indexInput by remember(rootId, node.id) { mutableStateOf("") }

    val clipboard = LocalClipboard.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(JwSpacing.large),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        JwText(
            text = node.displayLabel(),
            style = JwTheme.textStyles.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        NodeProperties(rootId = rootId, node = node)

        JwHorizontalDivider()

        JwSectionHeader(title = "Run an action on the app", contentPadding = PaddingValues(0.dp))
        NodeActionButtons(rootId = rootId, node = node, onPerformAction = onPerformAction)

        if (node.actions.contains("SetText")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                JwTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = "Text",
                    modifier = Modifier.weight(1f),
                )
                JwButton(
                    text = "Set text",
                    onClick = {
                        onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.SetText, text = textInput))
                    },
                    style = JwButtonStyle.Primary,
                )
            }
        }

        if (node.isScrollable) {
            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                JwButton(
                    text = "Scroll down",
                    onClick = {
                        onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollBy, scrollY = SCROLL_STEP_PX))
                    },
                )
                JwButton(
                    text = "Scroll up",
                    onClick = {
                        onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollBy, scrollY = -SCROLL_STEP_PX))
                    },
                )
            }
        }

        if (node.actions.contains("ScrollToIndex")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                JwTextField(
                    value = indexInput,
                    onValueChange = { indexInput = it },
                    placeholder = "Item index",
                    modifier = Modifier.weight(1f),
                )
                JwButton(
                    text = "Scroll to index",
                    onClick = {
                        indexInput.toIntOrNull()?.let { index ->
                            onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollToIndex, index = index))
                        }
                    },
                    enabled = indexInput.toIntOrNull() != null,
                )
            }
        }

        // The command-line tap for the node's platform: adb on Android, idb (iOS Development Bridge)
        // on iOS, whose `ui tap` takes the same points the node reports.
        val tapTool = if (node is AppleNode) "idb ui tap" else "adb shell input tap"
        JwButton(
            text = "Copy `$tapTool` for these bounds",
            onClick = {
                val command = "$tapTool ${node.boundsInScreen.centerX.roundToInt()} ${node.boundsInScreen.centerY.roundToInt()}"
                scope.launch { clipboard.setClipEntry(ClipEntry(StringSelection(command))) }
            },
            enabled = !node.boundsInScreen.isEmpty,
            style = JwButtonStyle.Text,
        )

        // Only an Android View has platform attributes to show. A Compose node's semantics are a
        // projection of composition state, so there is nothing here that could be edited to last.
        if (node is ViewNode) {
            JwHorizontalDivider()
            ViewAttributesPanel(state = viewAttributes, onCommit = onCommitViewAttribute)
        }
    }
}

private const val SCROLL_STEP_PX = 400f

@Composable
private fun NodeProperties(rootId: String, node: UiNode) {
    Column {
        PropertyRow("id", node.id.toString())
        PropertyRow("rootId", rootId)
        when (node) {
            is ViewNode -> {
                PropertyRow("viewClass", node.viewClass)
                node.resourceId?.let { PropertyRow("resourceId", "@id/$it") }
            }

            is ComposeNode -> {
                node.role?.let { PropertyRow("role", it) }
                node.testTag?.let { PropertyRow("testTag", it) }
                node.stateDescription?.let { PropertyRow("stateDescription", it, wrap = true) }
            }

            is AppleNode -> {
                PropertyRow("className", node.className)
                node.accessibilityIdentifier?.let { PropertyRow("accessibilityIdentifier", it) }
                node.accessibilityValue?.let { PropertyRow("accessibilityValue", it, wrap = true) }
                if (node.traits.isNotEmpty()) PropertyRow("traits", node.traits.joinToString(", "), wrap = true)
            }
        }
        node.text?.let { PropertyRow("text", it, wrap = true) }
        node.editableText?.let { PropertyRow("editableText", it, wrap = true) }
        node.contentDescription?.let { PropertyRow("contentDescription", it, wrap = true) }
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
        if (node.isInteractive && !node.isOperable) {
            PropertyRow("operable", "no — ${node.whyNotOperable()}", wrap = true)
        }
    }
}

@Composable
private fun NodeActionButtons(
    rootId: String,
    node: UiNode,
    onPerformAction: (PerformNodeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
    ) {
        ActionButton("Click", node, NodeAction.Click, rootId, onPerformAction)
        ActionButton("Long click", node, NodeAction.LongClick, rootId, onPerformAction)
        ActionButton("Focus", node, NodeAction.RequestFocus, rootId, onPerformAction)
        ActionButton("Dismiss", node, NodeAction.Dismiss, rootId, onPerformAction)
        ActionButton("Expand", node, NodeAction.Expand, rootId, onPerformAction)
        ActionButton("Collapse", node, NodeAction.Collapse, rootId, onPerformAction)
        // Offered even for a node whose reported bounds are empty: those are the clipped bounds,
        // and a node clipped away entirely is exactly the one this brings back.
        JwButton(
            text = "Bring into view",
            onClick = { onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.BringIntoView)) },
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    node: UiNode,
    action: NodeAction,
    rootId: String,
    onPerformAction: (PerformNodeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only offer what the node actually advertises: a button for an action the node does not expose
    // would always come back "not exposed", which is noise rather than feedback.
    val exposed = action.advertisedAs?.let(node.actions::contains) ?: true
    if (!exposed) return
    JwButton(
        text = label,
        onClick = { onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = action)) },
        modifier = modifier,
        enabled = node.isEnabled,
    )
}

/**
 * @param wrap `true` for values worth reading in full at a glance — the action list, the app's own
 *   strings. The rest stay on one scrollable line so the columns line up and a long id cannot push
 *   the properties below it off the pane.
 */
@Composable
private fun PropertyRow(label: String, value: String, wrap: Boolean = false) {
    JwKeyValueRow(key = label, value = value, keyWidth = PropertyKeyWidth, monospace = true, wrap = wrap)
}

private fun UiNode.whyNotOperable(): String {
    if (!isEnabled) return "disabled"
    return obscuredBy?.let { "#${it.nodeId} takes the touch (${it.rootId})" } ?: "nothing to aim at"
}
