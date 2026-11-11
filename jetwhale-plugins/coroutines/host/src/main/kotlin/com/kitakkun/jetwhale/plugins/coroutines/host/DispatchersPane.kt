package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSegmentedButtons
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStats
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.LongRun
import com.kitakkun.jetwhale.plugins.coroutines.protocol.UntrackedDispatcher
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

private val NumberColumnWidth = 72.dp

private val TimeColumnWidth = 110.dp

private val DispatcherTableMaxHeight = 200.dp

/** Enough for one table row or the header, with room to spare. */
private val DispatcherRowHeight = 32.dp

/** A long run shown with the dispatcher it held, since the list merges every tracked dispatcher. */
internal class DispatcherLongRun(val dispatcherName: String, val longRun: LongRun)

@Composable
internal fun DispatchersPane(report: DispatcherStatsReport?, actions: CoroutineInspectorActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when {
            report == null -> JwEmptyState(title = "Reading dispatchers…")

            report.dispatchers.isEmpty() && report.untracked.isEmpty() -> JwEmptyState(
                title = "No dispatchers tracked",
                description = "Wrap a dispatcher to see how long tasks wait for it and which ones hold it too long, then use the returned dispatcher where the app used the original:",
                action = { JwCodeBlock(text = TRACK_DISPATCHER_SNIPPET, copyLabel = "Copy") },
            )

            report.dispatchers.isEmpty() -> Column(Modifier.fillMaxSize()) {
                MetricsLegend("No dispatcher is tracked yet, so nothing is timed.")
                UntrackedDispatchers(report.untracked)
            }

            else -> DispatcherTables(report.dispatchers, report.untracked, actions)
        }
    }
}

/**
 * The dispatchers the app's coroutines run on without being tracked. The inspector sees coroutines,
 * not a dispatcher's tasks, so these get counts and a way to start timing them, not timings.
 */
@Composable
private fun UntrackedDispatchers(untracked: List<UntrackedDispatcher>) {
    JwSectionHeader(title = "Untracked dispatchers", count = untracked.size)
    JwTable(
        items = untracked,
        columns = listOf(
            JwTableColumn.text(header = "Dispatcher", width = JwColumnWidth.Weight(1f), text = UntrackedDispatcher::name),
            JwTableColumn.text(header = "Coroutines on it", width = JwColumnWidth.Weight(1f)) { formatStateCounts(it.coroutinesByState, separator = " · ") },
        ),
        key = UntrackedDispatcher::name,
        modifier = Modifier.fillMaxWidth().height((DispatcherRowHeight * (untracked.size + 1)).coerceAtMost(DispatcherTableMaxHeight)),
    )
    MetricsLegend("Coroutines in the registered scopes run on these, so they are counted here, but the app has not tracked them, so they have no wait or run times: timing needs every task to pass through the inspector, and a dispatcher such as Dispatchers.Default has no statistics to read from outside. To time one, track it once and use the returned dispatcher where the app used the original:")
    JwCodeBlock(text = trackingSnippet(untracked), copyLabel = "Copy", wrap = true, modifier = Modifier.padding(start = JwSpacing.large, end = JwSpacing.large, bottom = JwSpacing.large))
}

/**
 * The code that tracks [untracked]: one line per well-known dispatcher, with a threshold that suits
 * it, or a template when none is. `Dispatchers.Unconfined` has no dispatch to time and is left out.
 */
internal fun trackingSnippet(untracked: List<UntrackedDispatcher>): String {
    val known = untracked.mapNotNull { WellKnownDispatchers[it.name] }
    if (known.isEmpty()) return TRACK_DISPATCHER_SNIPPET
    return buildString {
        known.forEach { appendLine("val ${it.variableName} = inspector.track(${it.expression}, name = \"${it.trackedName}\", longRunThreshold = ${it.thresholdMillis}.milliseconds)") }
        append("// Use ${known.joinToString(" and ", transform = WellKnownDispatcher::variableName)} where the app used ${known.joinToString(" and ", transform = WellKnownDispatcher::expression)}.")
    }
}

private class WellKnownDispatcher(val expression: String, val variableName: String, val trackedName: String, val thresholdMillis: Int)

/** Keyed by how each dispatcher prints itself, which is what the agent reports. */
private val WellKnownDispatchers = listOf(
    WellKnownDispatcher(expression = "Dispatchers.Main", variableName = "main", trackedName = "Main", thresholdMillis = 16),
    WellKnownDispatcher(expression = "Dispatchers.Default", variableName = "default", trackedName = "Default", thresholdMillis = 100),
    WellKnownDispatcher(expression = "Dispatchers.IO", variableName = "io", trackedName = "IO", thresholdMillis = 500),
).associateBy(WellKnownDispatcher::expression)

/** How the long runs are listed: who held a thread, or every run in order. */
private enum class LongRunView(val label: String) {
    ByCoroutine("By coroutine"),
    EveryRun("Every run"),
}

@Composable
private fun DispatcherTables(dispatchers: List<DispatcherStats>, untracked: List<UntrackedDispatcher>, actions: CoroutineInspectorActions) {
    val longRuns = dispatchers.flatMap { stats -> stats.longRuns.map { DispatcherLongRun(stats.name, it) } }.sortedByDescending { it.longRun.atEpochMillis }
    var view by remember { mutableStateOf(LongRunView.ByCoroutine) }
    Column(Modifier.fillMaxSize()) {
        JwTable(
            items = dispatchers,
            columns = listOf(
                JwTableColumn.text(header = "Dispatcher", width = JwColumnWidth.Weight(1f)) { "${it.name} · long ≥ ${it.longRunThresholdMillis} ms" },
                numberColumn("Waiting") { it.queued.toString() },
                numberColumn("Running") { it.running.toString() },
                numberColumn("Finished") { it.completedTasks.toString() },
                numberColumn("Wait avg") { formatMillis(it.averageQueueLatencyMillis) },
                numberColumn("Wait max") { formatMillis(it.maxQueueLatencyMillis) },
                numberColumn("Run avg") { formatMillis(it.averageRunMillis) },
                numberColumn("Run max") { formatMillis(it.maxRunMillis) },
            ),
            key = DispatcherStats::name,
            modifier = Modifier.fillMaxWidth().height((DispatcherRowHeight * (dispatchers.size + 1)).coerceAtMost(DispatcherTableMaxHeight)),
        )
        MetricsLegend("Wait: from dispatch until a thread picks the task up — a long wait means the dispatcher is saturated. Run: how long a task held the thread before it suspended or finished. A long run held it at least the threshold next to the dispatcher's name; on Main, one past 16 ms drops a frame. The app keeps only its most recent long runs, so Times counts those.")
        JwSectionHeader(
            title = "Long runs",
            count = longRuns.size,
            trailing = {
                JwSegmentedButtons(options = LongRunView.entries, selected = view, onSelect = { view = it }, label = LongRunView::label)
                JwButton(text = "Clear", onClick = actions::clearLongRuns, style = JwButtonStyle.Text, enabled = longRuns.isNotEmpty())
            },
        )
        val emptyContent: @Composable () -> Unit = { JwEmptyState(title = "No long runs", description = "Tasks that hold a tracked dispatcher past its threshold appear here, with the name of their coroutine.") }
        when (view) {
            LongRunView.ByCoroutine -> JwTable(
                items = summarizeLongRuns(longRuns),
                columns = listOf(
                    JwTableColumn.text(header = "Coroutine", width = JwColumnWidth.Weight(2f)) { it.coroutineName ?: "(unnamed)" },
                    JwTableColumn.text(header = "Dispatcher", width = JwColumnWidth.Weight(1f), text = LongRunSummary::dispatcherName),
                    numberColumn("Times") { it.count.toString() },
                    numberColumn("Longest") { formatMillis(it.longestMillis) },
                    numberColumn("In all") { formatMillis(it.totalMillis) },
                    JwTableColumn.text(header = "Last at", width = JwColumnWidth.Fixed(TimeColumnWidth)) { TimeFormatter.format(Instant.ofEpochMilli(it.lastAtEpochMillis)) },
                ),
                modifier = Modifier.fillMaxWidth().weight(1f),
                emptyContent = emptyContent,
            )

            LongRunView.EveryRun -> JwTable(
                items = longRuns,
                columns = listOf(
                    JwTableColumn.text(header = "At", width = JwColumnWidth.Fixed(TimeColumnWidth)) { TimeFormatter.format(Instant.ofEpochMilli(it.longRun.atEpochMillis)) },
                    numberColumn("Held") { formatMillis(it.longRun.durationMillis) },
                    JwTableColumn.text(header = "Dispatcher", width = JwColumnWidth.Weight(1f), text = DispatcherLongRun::dispatcherName),
                    JwTableColumn.text(header = "Coroutine", width = JwColumnWidth.Weight(2f)) { it.longRun.coroutineName ?: "(unnamed)" },
                ),
                modifier = Modifier.fillMaxWidth().weight(1f),
                emptyContent = emptyContent,
            )
        }
        if (untracked.isNotEmpty()) UntrackedDispatchers(untracked)
    }
}

/** Every long run one coroutine had on one dispatcher, as far back as the dispatcher remembers. */
internal data class LongRunSummary(
    val dispatcherName: String,
    val coroutineName: String?,
    val count: Int,
    val longestMillis: Double,
    val totalMillis: Double,
    val lastAtEpochMillis: Long,
)

/** [runs] grouped by who held the thread, the ones that held it longest in all first. */
internal fun summarizeLongRuns(runs: List<DispatcherLongRun>): List<LongRunSummary> = runs
    .groupBy { it.dispatcherName to it.longRun.coroutineName }
    .map { (key, group) ->
        LongRunSummary(
            dispatcherName = key.first,
            coroutineName = key.second,
            count = group.size,
            longestMillis = group.maxOf { it.longRun.durationMillis },
            totalMillis = group.sumOf { it.longRun.durationMillis },
            lastAtEpochMillis = group.maxOf { it.longRun.atEpochMillis },
        )
    }
    .sortedByDescending(LongRunSummary::totalMillis)

private fun <T> numberColumn(header: String, text: (T) -> String): JwTableColumn<T> = JwTableColumn.text(header = header, width = JwColumnWidth.Fixed(NumberColumnWidth), alignment = Alignment.End, text = text)

private fun formatMillis(value: Double): String = if (value < 1_000) String.format(Locale.ROOT, "%.1f ms", value) else String.format(Locale.ROOT, "%.2f s", value / 1_000)

private const val TRACK_DISPATCHER_SNIPPET = """val main = inspector.track(Dispatchers.Main, name = "Main", longRunThreshold = 16.milliseconds)
val io = inspector.track(Dispatchers.IO, name = "IO", longRunThreshold = 500.milliseconds)"""
