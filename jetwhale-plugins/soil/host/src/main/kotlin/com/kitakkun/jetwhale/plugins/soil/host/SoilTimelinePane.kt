package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCheckbox
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent

/** A pause longer than this between two events splits them into separate bursts, such as two taps. */
private const val BURST_GAP_MILLIS = 1_000L

/** Fits `00:00:00.000`. */
private val TimeOfDayColumnWidth = 92.dp

/** Fits the longest event label, `Subscription failed`. */
private val KindColumnWidth = 128.dp

/** Fits `12.3s`. */
private val DurationColumnWidth = 52.dp

/**
 * A row of the timeline: events in a row of the same kind on the same entry, such as a subscription's
 * values, or the pause before a row that starts a new burst.
 */
private sealed interface TimelineRow {
    val key: String

    data class Pause(val beforeSequence: Long, val millis: Long) : TimelineRow {
        override val key: String get() = "pause:$beforeSequence"
    }

    /** Keyed by the first event, so the row stays put while repeats join it. */
    data class Events(val events: List<SoilEvent>) : TimelineRow {
        override val key: String get() = "events:${events.first().sequence}"
    }
}

/**
 * What happened to the entries, oldest first, with a marked pause between bursts so that what one
 * tap set off reads as one group. Selecting an event selects its entry; up and down move along.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SoilTimelinePane(
    events: List<SoilEvent>,
    selectedEventSequence: Long?,
    selectedHandle: String?,
    settings: SoilTimelineSettings,
    isFollowing: Boolean,
    timeOfDayFormatter: TimeOfDayFormatter,
    onSettingsChange: (SoilTimelineSettings) -> Unit,
    onFollowingChange: (Boolean) -> Unit,
    onSelectEvent: (Long) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownEvents = events.filter { settings.shows(it, selectedHandle) }
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.medium, vertical = JwSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
                verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                JwText(text = "Timeline", style = JwTheme.textStyles.label)
                JwText(text = if (shownEvents.size == events.size) "${events.size} events" else "${shownEvents.size} of ${events.size} events", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
                Spacer(Modifier.width(JwSpacing.small))
                SoilEventCategory.entries.forEach { category ->
                    val isSelected = category in settings.categories
                    JwTag(
                        text = "${category.label} ${events.count { it.kind.category == category }}",
                        tone = if (isSelected) JwTone.Accent else JwTone.Neutral,
                        style = if (isSelected) JwTagStyle.Filled else JwTagStyle.Outlined,
                        onClick = { onSettingsChange(settings.copy(categories = if (isSelected) settings.categories - category else settings.categories + category)) },
                    )
                }
            }
            JwCheckbox(checked = settings.isSelectedEntryOnly, label = "Selected entry only", onCheckedChange = { onSettingsChange(settings.copy(isSelectedEntryOnly = it)) })
            JwCheckbox(checked = isFollowing, label = "Follow newest", onCheckedChange = onFollowingChange)
            JwButton(text = "Clear", onClick = onClear, style = JwButtonStyle.Text, enabled = events.isNotEmpty())
        }
        JwHorizontalDivider()
        when {
            events.isEmpty() -> JwEmptyState(title = "No events yet", description = "Fetches, mutations and invalidations appear here as the app runs. Changes shorter than one reading of the cache can be missed.")
            shownEvents.isEmpty() -> JwEmptyState(title = "No matching events", description = if (settings.isSelectedEntryOnly && selectedHandle == null) "Select an entry to see its events." else "No event is in the selected categories.")
            else -> EventList(shownEvents, selectedEventSequence, selectedHandle, isFollowing, timeOfDayFormatter, onSelectEvent, onFollowingChange)
        }
    }
}

@Composable
private fun EventList(
    shownEvents: List<SoilEvent>,
    selectedEventSequence: Long?,
    selectedHandle: String?,
    isFollowing: Boolean,
    timeOfDayFormatter: TimeOfDayFormatter,
    onSelectEvent: (Long) -> Unit,
    onFollowingChange: (Boolean) -> Unit,
) {
    val eventRows = shownEvents.fold(mutableListOf<MutableList<SoilEvent>>()) { groups, event ->
        val group = groups.lastOrNull()?.takeIf { it.last().handle == event.handle && it.last().kind == event.kind && event.atEpochMillis - it.last().atEpochMillis <= BURST_GAP_MILLIS }
        if (group != null) group += event else groups += mutableListOf(event)
        groups
    }.map(TimelineRow::Events)
    val rows = eventRows.flatMapIndexed { index, row ->
        val pauseMillis = if (index == 0) 0 else row.events.first().atEpochMillis - eventRows[index - 1].events.last().atEpochMillis
        if (pauseMillis > BURST_GAP_MILLIS) listOf(TimelineRow.Pause(row.events.first().sequence, pauseMillis), row) else listOf(row)
    }
    val listState = rememberLazyListState()
    val focusRequester = remember(calculation = ::FocusRequester)
    // A request rather than scrollToItem: scrollToItem measures on the spot, and in a plugin scene
    // that measure can land while a composition is still pending, which breaks the scene.
    LaunchedEffect(rows.size, isFollowing) {
        if (isFollowing) listState.requestScrollToItem(rows.lastIndex)
    }
    fun selectRow(row: TimelineRow.Events) {
        onFollowingChange(false)
        onSelectEvent(row.events.last().sequence)
        listState.requestRevealItem(rows.indexOf(row))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val delta = when (keyEvent.key) {
                    Key.DirectionDown -> 1
                    Key.DirectionUp -> -1
                    else -> 0
                }
                if (keyEvent.type == KeyEventType.KeyDown && delta != 0) {
                    val current = eventRows.indexOfFirst { row -> row.events.any { it.sequence == selectedEventSequence } }
                    eventRows.getOrNull(if (current < 0) eventRows.lastIndex else (current + delta).coerceIn(0, eventRows.lastIndex))?.let(::selectRow)
                }
                delta != 0
            },
    ) {
        items(rows, key = TimelineRow::key) { row ->
            when (row) {
                is TimelineRow.Pause -> JwText(
                    text = "${describeDurationMillis(row.millis)} later",
                    style = JwTheme.textStyles.labelSmall,
                    color = JwTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = JwSpacing.tiny),
                )

                is TimelineRow.Events -> EventRow(
                    event = row.events.last(),
                    repeatCount = row.events.size,
                    isSelected = row.events.any { it.sequence == selectedEventSequence },
                    isOfSelectedEntry = row.events.last().handle == selectedHandle,
                    timeOfDayFormatter = timeOfDayFormatter,
                    onClick = {
                        focusRequester.requestFocus()
                        selectRow(row)
                    },
                )
            }
        }
    }
}

/**
 * One event on one line: when, what, to which entry, how long it took and what else it said. With
 * a [repeatCount] over one it stands for that many in a row, and shows the latest.
 */
@Composable
private fun EventRow(event: SoilEvent, repeatCount: Int, isSelected: Boolean, isOfSelectedEntry: Boolean, timeOfDayFormatter: TimeOfDayFormatter, onClick: () -> Unit) {
    JwListItem(selected = isSelected, onClick = onClick) {
        JwText(text = timeOfDayFormatter.formatTimeOfDay(event.atEpochMillis), style = JwTheme.textStyles.code, color = JwTheme.colors.textSecondary, modifier = Modifier.width(TimeOfDayColumnWidth))
        Row(Modifier.width(KindColumnWidth)) {
            JwTag(text = event.kind.label, tone = event.kind.tone, style = JwTagStyle.Tinted)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            JwText(
                text = event.entryId.label,
                style = JwTheme.textStyles.code,
                color = if (isOfSelectedEntry) JwTheme.colors.accent else JwTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (repeatCount > 1) JwTag(text = "×$repeatCount")
            event.detail?.let {
                JwText(text = it, style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
        }
        JwText(
            text = event.durationMillis?.let(::describeDurationMillis).orEmpty(),
            style = JwTheme.textStyles.labelSmall,
            color = JwTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(DurationColumnWidth),
        )
    }
}
