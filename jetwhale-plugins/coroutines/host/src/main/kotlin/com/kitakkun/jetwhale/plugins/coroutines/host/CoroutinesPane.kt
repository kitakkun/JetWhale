package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSegmentedButtons
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTooltip
import com.kitakkun.jetwhale.host.ui.JwTreeRow
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree

/** The state filters offered, each a set of states to show; the empty set shows everything. */
private enum class StateFilter(val label: String, val states: Set<CoroutineState>) {
    All("All", emptySet()),
    Active("Active", setOf(CoroutineState.Active)),
    New("Not started", setOf(CoroutineState.New)),
    Cancelling("Cancelling", setOf(CoroutineState.Cancelling)),
}

/** How long a coroutine must have been seen to be shown: the long-lived ones are where leaks and stalls hide. */
private enum class AgeFilter(val label: String, val minObservedMillis: Long?) {
    Any("Any age", null),
    TenSeconds("≥ 10 s", 10_000),
    OneMinute("≥ 1 min", 60_000),
}

private val FilterFieldWidth = 180.dp

/** The tree gets most of the width; the detail of the selected coroutine reads fine in the rest. */
private const val TREE_FRACTION = 0.6f

@Composable
internal fun CoroutinesPane(
    tree: CoroutineTree?,
    collapsed: Set<String>,
    filter: CoroutineFilter,
    selectedId: String?,
    lastSeenSelection: CoroutineLocation?,
    detail: CoroutineDetail?,
    actions: CoroutineInspectorActions,
    onFilterChange: (CoroutineFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        FilterBar(filter = filter, onFilterChange = onFilterChange)
        when {
            tree == null -> JwEmptyState(title = "Reading coroutines…")

            tree.roots.isEmpty() -> JwEmptyState(
                title = "No scopes registered",
                description = "The inspector shows the coroutines below the scopes the app registers. Register the scopes you want to watch where the app creates them:",
                action = { JwCodeBlock(text = REGISTER_SNIPPET, copyLabel = "Copy") },
            )

            else -> JwSplitPane(
                state = rememberJwSplitPaneState(TREE_FRACTION),
                first = { CoroutineTreeList(tree = tree, collapsed = collapsed, filter = filter, selectedId = selectedId, actions = actions) },
                second = {
                    CoroutineDetailPane(
                        current = selectedId?.let { findCoroutine(tree.roots, it) },
                        lastSeen = lastSeenSelection,
                        detail = detail,
                        onReloadStack = actions::reloadDetail,
                    )
                },
            )
        }
    }
}

private const val REGISTER_SNIPPET = """inspector.register(applicationScope, name = "Application")
inspector.register(viewModelScope, name = "HomeViewModel")"""

@Composable
private fun CoroutineTreeList(tree: CoroutineTree, collapsed: Set<String>, filter: CoroutineFilter, selectedId: String?, actions: CoroutineInspectorActions) {
    Column(Modifier.fillMaxSize()) {
        JwText(
            text = buildString {
                append("${tree.coroutineCount} coroutines")
                if (tree.truncated) append(" — the app has more; only the first ${tree.coroutineCount} are shown")
                append(" · Click one for its details")
            },
            style = JwTheme.textStyles.labelSmall,
            color = JwTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = JwSpacing.large, vertical = JwSpacing.small),
        )
        val rows = flattenCoroutineTree(filterCoroutineTree(tree.roots, filter), collapsed)
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = CoroutineRow::rowId) { row ->
                CoroutineTreeRow(
                    row = row,
                    selected = row.node.id == selectedId,
                    onSelect = { actions.select(row.node.id) },
                    onToggle = { actions.toggleCollapsed(row.rowId) },
                )
            }
        }
    }
}

@Composable
private fun FilterBar(filter: CoroutineFilter, onFilterChange: (CoroutineFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JwSegmentedButtons(
            options = StateFilter.entries,
            selected = StateFilter.entries.firstOrNull { it.states == filter.states } ?: StateFilter.All,
            onSelect = { onFilterChange(filter.copy(states = it.states)) },
            label = StateFilter::label,
        )
        JwSegmentedButtons(
            options = AgeFilter.entries,
            selected = AgeFilter.entries.firstOrNull { it.minObservedMillis == filter.minObservedMillis } ?: AgeFilter.Any,
            onSelect = { onFilterChange(filter.copy(minObservedMillis = it.minObservedMillis)) },
            label = AgeFilter::label,
        )
        JwTextField(
            value = filter.nameContains.orEmpty(),
            onValueChange = { onFilterChange(filter.copy(nameContains = it)) },
            placeholder = "Name",
            modifier = Modifier.width(FilterFieldWidth),
        )
        JwTextField(
            value = filter.dispatcherContains.orEmpty(),
            onValueChange = { onFilterChange(filter.copy(dispatcherContains = it)) },
            placeholder = "Dispatcher",
            modifier = Modifier.width(FilterFieldWidth),
        )
    }
}

@Composable
private fun CoroutineTreeRow(row: CoroutineRow, selected: Boolean, onSelect: () -> Unit, onToggle: () -> Unit) {
    val node = row.node
    JwTreeRow(
        text = node.name ?: node.description,
        depth = row.depth,
        expandable = node.children.isNotEmpty(),
        expanded = row.expanded,
        selected = selected,
        onClick = onSelect,
        onToggleExpanded = onToggle,
        muted = node.name == null,
        // State first: it is what a reader scans the tree for. The dispatcher, often the same down
        // a whole scope, comes last.
        trailingContent = {
            JwTooltip(text = node.state.explanation) {
                JwTag(text = node.state.label, tone = node.state.tone)
            }
            JwText(text = formatObserved(node.observedMillis), style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
            node.dispatcher?.let { JwText(text = it, style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
    )
}
