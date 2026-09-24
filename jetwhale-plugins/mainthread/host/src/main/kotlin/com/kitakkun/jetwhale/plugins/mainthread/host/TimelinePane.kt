package com.kitakkun.jetwhale.plugins.mainthread.host

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwColumnOverflow
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.mainthread.protocol.FrameStats
import com.kitakkun.jetwhale.plugins.mainthread.protocol.JankyFrame
import com.kitakkun.jetwhale.plugins.mainthread.protocol.LongTask
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport

/** The span the timeline shows, ending at the latest thing recorded: the last minute of use. */
private const val TIMELINE_WINDOW_MILLIS = 60_000L

private val TimelineHeight = 96.dp

private const val MIN_BAR_FRACTION = 0.15f
private val TimeColumnWidth = 104.dp
private val DurationColumnWidth = 88.dp

@Composable
internal fun TimelinePane(report: MainThreadReport, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        MainThreadTimeline(
            tasks = report.longTasks,
            jankyFrames = report.frames.recentJankyFrames,
            modifier = Modifier.fillMaxWidth().height(TimelineHeight).padding(horizontal = JwSpacing.large, vertical = JwSpacing.medium),
        )
        FrameSummary(report.frames, frameTiming = report.capabilities.frameTiming)
        JwHorizontalDivider()
        JwTable(
            items = report.longTasks.asReversed(),
            columns = listOf(
                JwTableColumn.text(header = "Started", width = JwColumnWidth.Fixed(TimeColumnWidth)) { formatTimeOfDay(it.startEpochMillis) },
                JwTableColumn.text(header = "Duration", width = JwColumnWidth.Fixed(DurationColumnWidth), alignment = Alignment.End) {
                    if (it.unresponsive) "${formatMillis(it.durationMillis)} !" else formatMillis(it.durationMillis)
                },
                JwTableColumn.text(header = "Task", width = JwColumnWidth.Weight(1f), overflow = JwColumnOverflow.Ellipsis, text = LongTask::label),
            ),
            modifier = Modifier.weight(1f),
            emptyContent = {
                JwEmptyState(
                    title = "No long tasks",
                    description = "Main-thread tasks of ${report.settings.longTaskThresholdMillis} ms or more show up here as they happen.",
                )
            },
        )
    }
}

/**
 * The last minute of the main thread: each long task a bar at its start, as tall as it was long
 * next to the longest one in view (red if it made the app unresponsive), and each janky frame a thin
 * tick along the bottom, so a stutter can be matched with the task that caused it.
 */
@Composable
private fun MainThreadTimeline(tasks: List<LongTask>, jankyFrames: List<JankyFrame>, modifier: Modifier = Modifier) {
    val taskColor = JwTheme.colors.warning
    val unresponsiveColor = JwTheme.colors.error
    val frameColor = JwTheme.colors.accent
    val axisColor = JwTheme.colors.border
    val latest = (tasks.map { it.startEpochMillis + it.durationMillis } + jankyFrames.map(JankyFrame::endEpochMillis)).maxOrNull()
    Canvas(modifier) {
        drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height))
        if (latest == null) return@Canvas
        val start = latest - TIMELINE_WINDOW_MILLIS
        fun x(epochMillis: Long): Float = ((epochMillis - start).toFloat() / TIMELINE_WINDOW_MILLIS) * size.width
        val tickHeight = size.height * 0.15f
        val barArea = size.height - tickHeight
        val shown = tasks.filter { it.startEpochMillis + it.durationMillis >= start }
        val longest = shown.maxOfOrNull(LongTask::durationMillis)?.toFloat() ?: 1f
        shown.forEach { task ->
            val left = x(task.startEpochMillis).coerceAtLeast(0f)
            val width = (x(task.startEpochMillis + task.durationMillis) - left).coerceAtLeast(2f)
            // The shortest recorded task still reads as a bar beside a much longer one.
            val height = barArea * (task.durationMillis / longest).coerceAtLeast(MIN_BAR_FRACTION)
            drawRect(
                color = if (task.unresponsive) unresponsiveColor else taskColor,
                topLeft = Offset(left, barArea - height),
                size = Size(width, height),
            )
        }
        jankyFrames.filter { it.endEpochMillis >= start }.forEach { frame ->
            val px = x(frame.endEpochMillis)
            drawLine(frameColor, Offset(px, size.height - tickHeight), Offset(px, size.height), strokeWidth = 2f)
        }
    }
}

@Composable
private fun FrameSummary(frames: FrameStats, frameTiming: Boolean) {
    val text = when {
        !frameTiming -> "Frame timing is not reported on this platform."
        frames.totalFrames == 0 -> "No frames measured yet."
        else -> "${frames.jankyFrames} of ${frames.totalFrames} frames janky · p50 ${formatFrameMillis(frames.p50Millis)} · p90 ${formatFrameMillis(frames.p90Millis)} · p99 ${formatFrameMillis(frames.p99Millis)} · slowest ${formatFrameMillis(frames.slowestMillis)}"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.small)) {
        JwText(text = text, style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
    }
}
