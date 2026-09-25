package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStats
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.LongRun
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

private val NumberColumnWidth = 88.dp

private val TimeColumnWidth = 110.dp

private val DispatcherTableMaxHeight = 200.dp

/** Enough for one table row or the header, with room to spare. */
private val DispatcherRowHeight = 32.dp

/** A long run shown with the dispatcher it held, since the list merges every tracked dispatcher. */
internal class DispatcherLongRun(val dispatcher: String, val run: LongRun)

@Composable
internal fun DispatchersPane(report: DispatcherStatsReport?, actions: CoroutineInspectorActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when {
            report == null -> JwEmptyState(title = "Reading dispatchers…")

            report.dispatchers.isEmpty() -> JwEmptyState(
                title = "No dispatchers tracked",
                description = "Wrap a dispatcher to see how long tasks wait for it and which ones hold it too long, then use the returned dispatcher where the app used the original:",
                action = { JwCodeBlock(text = TRACK_DISPATCHER_SNIPPET, copyLabel = "Copy") },
            )

            else -> DispatcherTables(report.dispatchers, actions)
        }
    }
}

/** How the long runs are listed: who held a thread, or every run in order. */
private enum class LongRunView(val label: String) {
    ByCoroutine("By coroutine"),
    EveryRun("Every run"),
}

@Composable
private fun DispatcherTables(dispatchers: List<DispatcherStats>, actions: CoroutineInspectorActions) {
    val longRuns = dispatchers.flatMap { stats -> stats.longRuns.map { DispatcherLongRun(stats.name, it) } }.sortedByDescending { it.run.atEpochMillis }
    var view by remember { mutableStateOf(LongRunView.ByCoroutine) }
    Column(Modifier.fillMaxSize()) {
        JwTable(
            items = dispatchers,
            columns = listOf(
                JwTableColumn.text(header = "Dispatcher", width = JwColumnWidth.Weight(1f), text = DispatcherStats::name),
                number("Waiting") { it.queued.toString() },
                number("Running") { it.running.toString() },
                number("Finished") { it.completedTasks.toString() },
                number("Wait avg") { millis(it.averageQueueLatencyMillis) },
                number("Wait max") { millis(it.maxQueueLatencyMillis) },
                number("Run avg") { millis(it.averageRunMillis) },
                number("Run max") { millis(it.maxRunMillis) },
                number("Long if ≥") { "${it.longRunThresholdMillis} ms" },
            ),
            key = DispatcherStats::name,
            // Sized to its rows rather than half the pane: an app tracks a handful of dispatchers.
            modifier = Modifier.fillMaxWidth().height((DispatcherRowHeight * (dispatchers.size + 1)).coerceAtMost(DispatcherTableMaxHeight)),
        )
        MetricsLegend("Wait: from dispatch until a thread picks the task up — a long wait means the dispatcher is saturated. Run: how long a task held the thread before it suspended or finished. A long run held it at least the threshold; on Main, one past 16 ms drops a frame.")
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
                    JwTableColumn.text(header = "Dispatcher", width = JwColumnWidth.Weight(1f), text = LongRunHolder::dispatcher),
                    number("Times") { it.count.toString() },
                    number("Longest") { millis(it.longestMillis) },
                    number("In all") { millis(it.totalMillis) },
                    JwTableColumn.text(header = "Last at", width = JwColumnWidth.Fixed(TimeColumnWidth)) { TimeFormatter.format(Instant.ofEpochMilli(it.lastAtEpochMillis)) },
                ),
                modifier = Modifier.fillMaxWidth().weight(1f),
                emptyContent = emptyContent,
            )

            LongRunView.EveryRun -> JwTable(
                items = longRuns,
                columns = listOf(
                    JwTableColumn.text(header = "At", width = JwColumnWidth.Fixed(TimeColumnWidth)) { TimeFormatter.format(Instant.ofEpochMilli(it.run.atEpochMillis)) },
                    number("Held") { millis(it.run.durationMillis) },
                    JwTableColumn.text(header = "Dispatcher", width = JwColumnWidth.Weight(1f), text = DispatcherLongRun::dispatcher),
                    JwTableColumn.text(header = "Coroutine", width = JwColumnWidth.Weight(2f)) { it.run.coroutineName ?: "(unnamed)" },
                ),
                modifier = Modifier.fillMaxWidth().weight(1f),
                emptyContent = emptyContent,
            )
        }
    }
}

/** Every long run one coroutine had on one dispatcher, as far back as the dispatcher remembers. */
internal data class LongRunHolder(
    val dispatcher: String,
    val coroutineName: String?,
    val count: Int,
    val longestMillis: Double,
    val totalMillis: Double,
    val lastAtEpochMillis: Long,
)

/** [runs] grouped by who held the thread, the ones that held it longest in all first. */
internal fun summarizeLongRuns(runs: List<DispatcherLongRun>): List<LongRunHolder> = runs
    .groupBy { it.dispatcher to it.run.coroutineName }
    .map { (key, group) ->
        LongRunHolder(
            dispatcher = key.first,
            coroutineName = key.second,
            count = group.size,
            longestMillis = group.maxOf { it.run.durationMillis },
            totalMillis = group.sumOf { it.run.durationMillis },
            lastAtEpochMillis = group.maxOf { it.run.atEpochMillis },
        )
    }
    .sortedByDescending(LongRunHolder::totalMillis)

private fun <T> number(header: String, text: (T) -> String): JwTableColumn<T> = JwTableColumn.text(header = header, width = JwColumnWidth.Fixed(NumberColumnWidth), alignment = Alignment.End, text = text)

private fun millis(value: Double): String = if (value < 1_000) String.format(Locale.ROOT, "%.1f ms", value) else String.format(Locale.ROOT, "%.2f s", value / 1_000)

private const val TRACK_DISPATCHER_SNIPPET = """val main = inspector.track(Dispatchers.Main, name = "Main", longRunThreshold = 16.milliseconds)
val io = inspector.track(Dispatchers.IO, name = "IO", longRunThreshold = 500.milliseconds)"""
