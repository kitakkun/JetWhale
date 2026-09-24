package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** Often enough to watch a coroutine get stuck, rarely enough to cost the app next to nothing. */
private val RefreshInterval = 1.seconds

/**
 * Binds the host-owned state to [CoroutineInspectorScreen] and keeps the visible tab current while
 * it is shown and live. The dump is read only on request: it walks every coroutine's stack.
 */
@Composable
internal fun CoroutineInspectorScreenRoot(state: CoroutineInspectorState, modifier: Modifier = Modifier) {
    var tab by rememberPersistent("tab", default = InspectorTab.Coroutines)
    var live by rememberPersistent("live", default = true)
    var filter by remember { mutableStateOf(CoroutineFilter.None) }
    LaunchedEffect(tab, live) {
        if (tab == InspectorTab.Dump) return@LaunchedEffect
        state.refresh(tab)
        while (live) {
            delay(RefreshInterval)
            state.refresh(tab)
        }
    }
    CoroutineInspectorScreen(
        tab = tab,
        live = live,
        filter = filter,
        tree = state.tree,
        collapsed = state.collapsed,
        dispatchers = state.dispatchers,
        flows = state.flows,
        dump = state.dump,
        status = state.status,
        actions = state,
        onSelectTab = { tab = it },
        onLiveChange = { live = it },
        onFilterChange = { filter = it },
        modifier = modifier,
    )
}

@Composable
internal fun CoroutineInspectorScreen(
    tab: InspectorTab,
    live: Boolean,
    filter: CoroutineFilter,
    tree: CoroutineTree?,
    collapsed: Set<String>,
    dispatchers: DispatcherStatsReport?,
    flows: TrackedFlowReport?,
    dump: CoroutineDump?,
    status: InspectorStatus?,
    actions: CoroutineInspectorActions,
    onSelectTab: (InspectorTab) -> Unit,
    onLiveChange: (Boolean) -> Unit,
    onFilterChange: (CoroutineFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            title = "Coroutines",
            actions = {
                if (tab != InspectorTab.Dump) {
                    JwButton(text = if (live) "Pause" else "Resume", onClick = { onLiveChange(!live) }, style = JwButtonStyle.Text)
                }
                JwButton(text = "Refresh", onClick = { actions.refresh(tab) }, style = JwButtonStyle.Text)
            },
        )
        JwTabRow {
            InspectorTab.entries.forEach { entry ->
                JwTab(selected = entry == tab, text = entry.label, onClick = { onSelectTab(entry) })
            }
        }
        status?.let { JwBanner(text = it.message, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        when (tab) {
            InspectorTab.Coroutines -> CoroutinesPane(tree = tree, collapsed = collapsed, filter = filter, actions = actions, onFilterChange = onFilterChange)
            InspectorTab.Dispatchers -> DispatchersPane(report = dispatchers, actions = actions)
            InspectorTab.Flows -> FlowsPane(report = flows)
            InspectorTab.Dump -> DumpPane(dump = dump, onDump = { actions.refresh(InspectorTab.Dump) })
        }
    }
}
