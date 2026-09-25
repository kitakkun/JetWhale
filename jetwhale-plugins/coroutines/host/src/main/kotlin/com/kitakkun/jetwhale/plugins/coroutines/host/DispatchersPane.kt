package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
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

/** A long run shown with the dispatcher it held, since the list merges every tracked dispatcher. */
private class DispatcherLongRun(val dispatcher: String, val run: LongRun)

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

@Composable
private fun DispatcherTables(dispatchers: List<DispatcherStats>, actions: CoroutineInspectorActions) {
    val longRuns = dispatchers.flatMap { stats -> stats.longRuns.map { DispatcherLongRun(stats.name, it) } }.sortedByDescending { it.run.atEpochMillis }
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
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        MetricsLegend("Wait: from dispatch until a thread picks the task up — a long wait means the dispatcher is saturated. Run: how long a task held the thread before it suspended or finished. A long run held it at least the threshold; on Main, one past 16 ms drops a frame.")
        JwSectionHeader(
            title = "Long runs",
            count = longRuns.size,
            trailing = { JwButton(text = "Clear", onClick = actions::clearLongRuns, style = JwButtonStyle.Text, enabled = longRuns.isNotEmpty()) },
        )
        JwTable(
            items = longRuns,
            columns = listOf(
                JwTableColumn.text(header = "At", width = JwColumnWidth.Fixed(110.dp)) { TimeFormatter.format(Instant.ofEpochMilli(it.run.atEpochMillis)) },
                number("Held") { millis(it.run.durationMillis) },
                JwTableColumn.text(header = "Dispatcher", width = JwColumnWidth.Weight(1f), text = DispatcherLongRun::dispatcher),
                JwTableColumn.text(header = "Coroutine", width = JwColumnWidth.Weight(2f)) { it.run.coroutineName ?: "(unnamed)" },
            ),
            modifier = Modifier.fillMaxWidth().weight(1f),
            emptyContent = { JwEmptyState(title = "No long runs", description = "Tasks that hold a tracked dispatcher past its threshold appear here.") },
        )
    }
}

private fun <T> number(header: String, text: (T) -> String): JwTableColumn<T> = JwTableColumn.text(header = header, width = JwColumnWidth.Fixed(NumberColumnWidth), alignment = Alignment.End, text = text)

private fun millis(value: Double): String = String.format(Locale.ROOT, "%.1f ms", value)

private const val TRACK_DISPATCHER_SNIPPET = """val main = inspector.track(Dispatchers.Main, name = "Main", longRunThreshold = 16.milliseconds)
val io = inspector.track(Dispatchers.IO, name = "IO", longRunThreshold = 500.milliseconds)"""
