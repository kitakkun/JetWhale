package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStats
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.FlowValue
import com.kitakkun.jetwhale.plugins.coroutines.protocol.LongRun
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowInfo
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport

private val previewTree = CoroutineTree(
    roots = listOf(
        node(
            "c1",
            "Application",
            CoroutineState.Active,
            120_000,
            listOf(
                node("c2", "sync", CoroutineState.Active, 95_000, listOf(node("c3", "download", CoroutineState.Active, 4_200, emptyList()))),
                node("c4", null, CoroutineState.Cancelling, 61_000, emptyList()),
            ),
        ),
    ),
    coroutineCount = 4,
    truncated = false,
    capturedAtEpochMillis = 1_760_000_000_000,
)

private val previewDispatchers = DispatcherStatsReport(
    dispatchers = listOf(
        DispatcherStats(
            name = "Main",
            queued = 2,
            running = 1,
            completedTasks = 1_204,
            averageQueueLatencyMillis = 0.8,
            maxQueueLatencyMillis = 41.0,
            averageRunMillis = 1.2,
            maxRunMillis = 152.3,
            longRunThresholdMillis = 16,
            longRuns = listOf(LongRun(atEpochMillis = 1_760_000_000_000, durationMillis = 152.3, coroutineName = "parse-feed")),
        ),
    ),
    capturedAtEpochMillis = 1_760_000_000_000,
)

private val previewFlows = TrackedFlowReport(
    flows = listOf(
        TrackedFlowInfo(
            name = "prices",
            activeCollectors = 1,
            collections = 3,
            completions = 0,
            cancellations = 2,
            failures = 0,
            emissions = 481,
            emissionsPerSecond = 2.0,
            recentValues = listOf(FlowValue(atEpochMillis = 1_760_000_000_000, text = "Price(symbol=KT, value=42.0)")),
        ),
    ),
    capturedAtEpochMillis = 1_760_000_000_000,
)

private object NoActions : CoroutineInspectorActions {
    override fun refresh(tab: InspectorTab) = Unit

    override fun toggleCollapsed(rowId: String) = Unit

    override fun clearLongRuns() = Unit
}

@Preview
@Composable
private fun CoroutineInspectorScreenPreview() {
    JwTheme(darkTheme = false) {
        CoroutineInspectorScreen(
            tab = InspectorTab.Coroutines,
            live = true,
            filter = CoroutineFilter.None,
            tree = previewTree,
            collapsed = emptySet(),
            dispatchers = previewDispatchers,
            flows = previewFlows,
            dump = null,
            status = null,
            actions = NoActions,
            onSelectTab = {},
            onLiveChange = {},
            onFilterChange = {},
        )
    }
}

@Preview
@Composable
private fun CoroutinesPanePreview() {
    JwTheme(darkTheme = true) {
        CoroutinesPane(tree = previewTree, collapsed = setOf("c1/c2"), filter = CoroutineFilter.None, actions = NoActions, onFilterChange = {})
    }
}

@Preview
@Composable
private fun DispatchersPanePreview() {
    JwTheme(darkTheme = false) {
        DispatchersPane(report = previewDispatchers, actions = NoActions)
    }
}

@Preview
@Composable
private fun FlowsPanePreview() {
    JwTheme(darkTheme = false) {
        FlowsPane(report = previewFlows)
    }
}

@Preview
@Composable
private fun DumpPanePreview() {
    JwTheme(darkTheme = false) {
        DumpPane(dump = CoroutineDump(text = "", unavailableReason = "Kotlin/Native has no DebugProbes"), onDump = {})
    }
}

private fun node(id: String, name: String?, state: CoroutineState, observedMillis: Long, children: List<CoroutineNode>) = CoroutineNode(
    id = id,
    name = name,
    state = state,
    observedMillis = observedMillis,
    dispatcher = "Dispatchers.Default",
    description = "StandaloneCoroutine{${state.name}}@1a2b3c",
    children = children,
)
