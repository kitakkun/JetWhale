package com.kitakkun.jetwhale.plugins.background.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSegmentedButtons
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkSnapshot
import com.kitakkun.jetwhale.plugins.background.protocol.WorkSourceInfo
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState

/** The list is the thing being scanned; the detail beside it reads one item at a time. */
private const val LIST_FRACTION = 0.55f

/** Fits "Filter by name, tag or id" without taking the row from the scope buttons. */
private val QueryFieldWidth = 280.dp

/** The state groups the filter offers, from the states each one shows. */
internal enum class StateScope(val label: String, val states: Set<WorkState>) {
    All("All", emptySet()),
    Pending("Pending", UNFINISHED_STATES),
    Finished("Finished", FINISHED_STATES),
}

/** Binds the browser the plugin owns to [BackgroundWorkScreen]. */
@Composable
internal fun BackgroundWorkScreenRoot(browser: BackgroundWorkBrowser, modifier: Modifier = Modifier) {
    BackgroundWorkScreen(
        snapshot = browser.snapshot,
        visibleItems = browser.visibleItems,
        selectedItem = browser.selectedItem,
        history = browser.history,
        filter = browser.filter,
        status = browser.status,
        actions = browser,
        modifier = modifier,
    )
}

@Composable
internal fun BackgroundWorkScreen(
    snapshot: BackgroundWorkSnapshot?,
    visibleItems: List<BackgroundWorkItem>,
    selectedItem: BackgroundWorkItem?,
    history: Map<WorkKey, List<StateTransition>>,
    filter: WorkFilter,
    status: WorkStatus?,
    actions: BackgroundWorkActions,
    modifier: Modifier = Modifier,
) {
    var cancellingUnique by remember { mutableStateOf(false) }
    val uniqueNameSources = snapshot?.sources.orEmpty().filter { it.available && it.supportsCancelByUniqueName }
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            title = "Background Work",
            actions = {
                if (uniqueNameSources.isNotEmpty()) {
                    JwButton(text = "Cancel unique work…", onClick = { cancellingUnique = true }, style = JwButtonStyle.Text)
                }
                JwButton(text = "Reload from app", onClick = actions::refresh, style = JwButtonStyle.Text)
            },
        )
        status?.let { JwBanner(text = it.message, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        snapshot?.sources?.takeIf(List<WorkSourceInfo>::isNotEmpty)?.let { SourceStrip(it) }
        FilterRow(filter = filter, onFilterChange = actions::changeFilter)
        WorkContent(snapshot = snapshot, visibleItems = visibleItems, selectedItem = selectedItem, history = history, actions = actions)
    }
    if (cancellingUnique) {
        CancelUniqueWorkDialog(
            sourceNames = uniqueNameSources.map(WorkSourceInfo::name),
            onCancel = { source, target ->
                cancellingUnique = false
                actions.cancel(source, target)
            },
            onDismiss = { cancellingUnique = false },
        )
    }
}

@Composable
private fun WorkContent(
    snapshot: BackgroundWorkSnapshot?,
    visibleItems: List<BackgroundWorkItem>,
    selectedItem: BackgroundWorkItem?,
    history: Map<WorkKey, List<StateTransition>>,
    actions: BackgroundWorkActions,
) {
    when {
        snapshot == null -> JwEmptyState(title = "Loading", description = "Asking the app for its background work.")

        snapshot.sources.isEmpty() -> JwEmptyState(
            title = "No schedulers",
            description = "The app reports no scheduler. Android and iOS have one by default; add the WorkManager adapter for WorkManager, or pass your own BackgroundWorkSource.",
        )

        else -> JwSplitPane(
            state = rememberJwSplitPaneState(LIST_FRACTION),
            first = { WorkTable(items = visibleItems, selectedKey = selectedItem?.key, onSelect = actions::select) },
            second = {
                if (selectedItem == null) {
                    JwEmptyState(title = "Nothing selected", description = "Pick a piece of work to see its details, history and actions.")
                } else {
                    val source = snapshot.sources.firstOrNull { it.name == selectedItem.source }
                    WorkDetail(item = selectedItem, source = source, history = history[selectedItem.key].orEmpty(), actions = actions)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceStrip(sources: List<WorkSourceInfo>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.small),
    ) {
        sources.forEach { source ->
            JwTag(
                text = if (source.available) source.name else "${source.name}: ${source.unavailableReason ?: "unavailable"}",
                tone = if (source.available) JwTone.Neutral else JwTone.Warning,
            )
        }
    }
}

@Composable
private fun FilterRow(filter: WorkFilter, onFilterChange: (WorkFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JwSegmentedButtons(
            options = StateScope.entries,
            selected = StateScope.entries.firstOrNull { it.states == filter.states } ?: StateScope.All,
            onSelect = { onFilterChange(filter.copy(states = it.states)) },
            label = StateScope::label,
        )
        JwTextField(
            value = filter.query,
            onValueChange = { onFilterChange(filter.copy(query = it)) },
            placeholder = "Filter by name, tag or id",
            modifier = Modifier.width(QueryFieldWidth),
        )
    }
}
