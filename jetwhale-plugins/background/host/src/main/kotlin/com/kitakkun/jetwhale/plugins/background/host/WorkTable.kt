package com.kitakkun.jetwhale.plugins.background.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Fits "Scheduled", the longest state name, as a tag. */
private val StateColumnWidth = 104.dp

/** Fits "JobScheduler", the longest built-in source name. */
private val SourceColumnWidth = 112.dp

/** Fits the "Earliest run" header and a same-day time with seconds. */
private val NextRunColumnWidth = 104.dp

internal val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

internal val DateTimeFormatterLong: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
internal fun WorkTable(
    items: List<BackgroundWorkItem>,
    selectedKey: WorkKey?,
    onSelect: (WorkKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    JwTable(
        items = items,
        columns = listOf(
            JwTableColumn.text(header = "Name", width = JwColumnWidth.Weight(2f)) { shortName(it.name) },
            JwTableColumn(header = "State", width = JwColumnWidth.Fixed(StateColumnWidth)) { item -> StateTag(item.state) },
            JwTableColumn.text(header = "Tags", width = JwColumnWidth.Weight(1f)) { it.tags.joinToString() },
            JwTableColumn.text(header = "Earliest run", width = JwColumnWidth.Fixed(NextRunColumnWidth)) { item ->
                item.nextRunEpochMillis?.let { TimeFormatter.format(Instant.ofEpochMilli(it)) }.orEmpty()
            },
            JwTableColumn.text(header = "Source", width = JwColumnWidth.Fixed(SourceColumnWidth), text = BackgroundWorkItem::source),
        ),
        key = BackgroundWorkItem::key,
        isSelected = { it.key == selectedKey },
        onClick = { onSelect(it.key) },
        modifier = modifier,
        emptyContent = { JwEmptyState(title = "No work", description = "Nothing matches the filter, or the app has no background work right now.") },
    )
}

@Composable
internal fun StateTag(state: WorkState) {
    JwTag(text = state.name, tone = toneOf(state))
}

private fun toneOf(state: WorkState): JwTone = when (state) {
    WorkState.Running -> JwTone.Accent
    WorkState.Succeeded -> JwTone.Success
    WorkState.Failed -> JwTone.Error
    WorkState.Cancelled -> JwTone.Neutral
    WorkState.Enqueued, WorkState.Blocked, WorkState.Scheduled -> JwTone.Info
}
