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
import androidx.compose.runtime.rememberUpdatedState
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
        highlightStatus?.let { JwStatusLine(text = "Highlight: $it", tone = JwTone.Warning) }
    }
}

@Composable
private fun EmptyTreeMessage(snapshot: NodeTreeSnapshot?, search: String, interactiveOnly: Boolean) {
    val (title, description) = when {
        snapshot == null -> "Not captured yet" to "Press Refresh to capture the app's node tree."

        snapshot.roots.isEmpty() ->
            "No root reported" to
                "Install a probe: installJetWhaleSemanticsProbe(application) on Android, installJetWhaleSemanticsProbe() on iOS, or JetWhaleSemanticsProbe() inside your composition."

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
    val hoverInteractionSource = remember(calculation = ::MutableInteractionSource)
    val hovered by hoverInteractionSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) { onHoverChange(hovered) }
    // A row filtered or collapsed away under a resting pointer never reports leaving on its own, and
    // the box would stay on a node the pointer is no longer over.
    val currentOnHoverChange by rememberUpdatedState(onHoverChange)
    DisposableEffect(Unit) {
        onDispose { currentOnHoverChange(false) }
    }
    JwTreeRow(
        modifier = modifier.hoverable(hoverInteractionSource),
        text = label,
        depth = row.depth,
        expandable = row.expandable,
        expanded = row.expanded,
        selected = selected,
        muted = !row.node.isVisible,
        onClick = onSelect,
        onToggleExpanded = onToggleExpanded,
        trailingContent = {
            when (row.node) {
                is ViewNode -> JwTag(text = "View", tone = JwTone.Info)
                is AppleNode -> JwTag(text = "iOS", tone = JwTone.Info)
                is ComposeNode -> Unit
            }
            if (row.node.isInteractive) {
                JwTag(text = row.node.actionSummary(), tone = JwTone.Accent)
            }
            if (row.node.isInteractive && !row.node.isOperable) {
                JwTag(text = "not operable", tone = JwTone.Warning)
            }
            if (!row.node.isLabeledById) {
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
