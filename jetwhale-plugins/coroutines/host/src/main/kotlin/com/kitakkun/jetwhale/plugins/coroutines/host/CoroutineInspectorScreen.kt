package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.JwTooltip
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

/** Often enough to watch a coroutine get stuck, rarely enough to cost the app next to nothing. */
private val RefreshInterval = 1.seconds

/**
 * Binds the host-owned state to [CoroutineInspectorScreen] and keeps the visible tab current while
 * it is shown and auto-refresh is on. The dump is read only on request: it walks every coroutine's stack.
 */
@Composable
internal fun CoroutineInspectorScreenRoot(state: CoroutineInspectorState, modifier: Modifier = Modifier) {
    var tab by rememberPersistent("tab", default = InspectorTab.Coroutines)
    var autoRefresh by rememberPersistent("autoRefresh", default = true)
    var filter by remember { mutableStateOf(CoroutineFilter.None) }
    LaunchedEffect(tab, autoRefresh) {
        if (tab == InspectorTab.Dump) return@LaunchedEffect
        state.refresh(tab)
        while (autoRefresh) {
            delay(RefreshInterval)
            state.refresh(tab)
        }
    }
    CoroutineInspectorScreen(
        tab = tab,
        autoRefresh = autoRefresh,
        filter = filter,
        tree = state.tree,
        collapsed = state.collapsed,
        selectedId = state.selectedId,
        lastSeenSelection = state.lastSeenSelection,
        detail = state.detail,
        dispatchers = state.dispatchers,
        flows = state.flows,
        dump = state.dump,
        status = state.status,
        actions = state,
        onSelectTab = { tab = it },
        onAutoRefreshChange = { autoRefresh = it },
        onFilterChange = { filter = it },
        modifier = modifier,
    )
}

@Composable
internal fun CoroutineInspectorScreen(
    tab: InspectorTab,
    autoRefresh: Boolean,
    filter: CoroutineFilter,
    tree: CoroutineTree?,
    collapsed: Set<String>,
    selectedId: String?,
    lastSeenSelection: CoroutineLocation?,
    detail: CoroutineDetail?,
    dispatchers: DispatcherStatsReport?,
    flows: TrackedFlowReport?,
    dump: CoroutineDump?,
    status: InspectorStatus?,
    actions: CoroutineInspectorActions,
    onSelectTab: (InspectorTab) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onFilterChange: (CoroutineFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val capturedAtEpochMillis = when (tab) {
        InspectorTab.Coroutines -> tree?.capturedAtEpochMillis
        InspectorTab.Dispatchers -> dispatchers?.capturedAtEpochMillis
        InspectorTab.Flows -> flows?.capturedAtEpochMillis
        InspectorTab.Dump -> null
    }
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            title = "Coroutines",
            actions = {
                if (tab == InspectorTab.Dump) {
                    JwText(text = "A dump is taken only when you ask for one", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
                } else {
                    JwText(text = refreshStatus(autoRefresh, capturedAtEpochMillis), style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
                    JwTooltip(text = "Reads this tab from the app again every second while it is open. Turning it off freezes what you see here; the app itself keeps running.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                            JwText(text = "Auto-refresh", style = JwTheme.textStyles.label)
                            JwSwitch(checked = autoRefresh, contentDescription = "Auto-refresh", onCheckedChange = onAutoRefreshChange)
                        }
                    }
                    JwButton(text = "Refresh now", onClick = { actions.refresh(tab) }, style = JwButtonStyle.Text)
                }
            },
        )
        JwTabRow {
            InspectorTab.entries.forEach { entry ->
                JwTab(selected = entry == tab, text = entry.label, onClick = { onSelectTab(entry) })
            }
        }
        status?.let { JwBanner(text = it.message, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        when (tab) {
            InspectorTab.Coroutines -> CoroutinesPane(
                tree = tree,
                collapsed = collapsed,
                filter = filter,
                selectedId = selectedId,
                lastSeenSelection = lastSeenSelection,
                detail = detail,
                actions = actions,
                onFilterChange = onFilterChange,
            )

            InspectorTab.Dispatchers -> DispatchersPane(report = dispatchers, actions = actions)

            InspectorTab.Flows -> FlowsPane(report = flows)

            InspectorTab.Dump -> DumpPane(dump = dump, onDump = { actions.refresh(InspectorTab.Dump) })
        }
    }
}

private val SnapshotTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun refreshStatus(autoRefresh: Boolean, capturedAtEpochMillis: Long?): String = when {
    capturedAtEpochMillis == null -> "Reading from the app…"
    autoRefresh -> "Live — updated every second"
    else -> "Paused — showing the app as of ${SnapshotTimeFormatter.format(Instant.ofEpochMilli(capturedAtEpochMillis))}"
}
