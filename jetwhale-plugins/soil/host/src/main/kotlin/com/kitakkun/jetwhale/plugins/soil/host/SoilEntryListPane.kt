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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwStatusDot
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind

/** Fits the longest choice, `Sort: Recent activity`. */
private val SortMenuWidth = 176.dp

/** A row of the entry list: a kind's header while the list is grouped, or an entry. */
private sealed interface EntryListRow {
    val key: String

    data class Header(val kind: SoilEntryKind, val count: Int) : EntryListRow {
        override val key: String get() = "kind:${kind.name}"
    }

    data class Entry(val listed: ListedSoilEntry) : EntryListRow {
        override val key: String get() = listed.entry.handle
    }
}

/**
 * The entries [settings] lets through: a search field and the sort above the condition chips, each
 * chip with how many entries meet it, then the list. Up and down move the selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SoilEntryListPane(
    listedEntries: List<ListedSoilEntry>,
    selectedHandle: String?,
    settings: SoilEntryListSettings,
    listFilter: SoilEntryListFilter,
    agentNowEpochSeconds: Long,
    onSettingsChange: (SoilEntryListSettings) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownEntries = listFilter.shownEntriesOf(listedEntries, settings)
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = JwSpacing.medium, end = JwSpacing.medium, top = JwSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JwSearchField(
                value = settings.searchText,
                clearLabel = "Clear search",
                onValueChange = { onSettingsChange(settings.copy(searchText = it)) },
                placeholder = "Namespace or tag",
                modifier = Modifier.weight(1f),
            )
            SortMenu(sort = settings.sort, modifier = Modifier.width(SortMenuWidth), onSortChange = { onSettingsChange(settings.copy(sort = it)) })
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(JwSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
        ) {
            SoilEntryCondition.entries.forEach { condition ->
                if (condition.ordinal > 0 && SoilEntryCondition.entries[condition.ordinal - 1].group != condition.group) Spacer(Modifier.width(JwSpacing.medium))
                ConditionChip(
                    condition = condition,
                    count = listFilter.countMeeting(listedEntries, settings.searchText, condition),
                    isSelected = condition in settings.conditions,
                    onToggle = { onSettingsChange(settings.copy(conditions = if (condition in settings.conditions) settings.conditions - condition else settings.conditions + condition)) },
                )
            }
        }
        if (settings.isNarrowed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JwText(text = "Showing ${shownEntries.size} of ${listedEntries.size}", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
                JwButton(text = "Clear filters", onClick = { onSettingsChange(settings.copy(searchText = "", conditions = emptySet())) }, style = JwButtonStyle.Text)
            }
        }
        when {
            listedEntries.isEmpty() -> JwEmptyState(title = "The cache is empty", description = "Entries appear as the app uses its queries, mutations and subscriptions.")
            shownEntries.isEmpty() -> JwEmptyState(title = "No matches", description = "No entry matches the search and the selected conditions.")
            else -> EntryList(shownEntries, isGroupedByKind = settings.sort == SoilEntrySort.KIND, selectedHandle, agentNowEpochSeconds, onSelect)
        }
    }
}

@Composable
private fun SortMenu(sort: SoilEntrySort, modifier: Modifier, onSortChange: (SoilEntrySort) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    JwDropdownButton(text = "Sort: ${sort.label}", expanded = isExpanded, onExpandedChange = { isExpanded = it }, modifier = modifier) {
        SoilEntrySort.entries.forEach { option ->
            JwMenuItem(
                text = option.label,
                selected = option == sort,
                onClick = {
                    onSortChange(option)
                    isExpanded = false
                },
            )
        }
    }
}

/** A toggle for one condition: filled while selected, tinted while entries meet a worrying one. */
@Composable
private fun ConditionChip(condition: SoilEntryCondition, count: Int, isSelected: Boolean, onToggle: () -> Unit) {
    JwTag(
        text = "${condition.label} $count",
        tone = when {
            isSelected -> JwTone.Accent
            count == 0 -> JwTone.Neutral
            else -> condition.tone
        },
        style = when {
            isSelected -> JwTagStyle.Filled
            count > 0 && condition.tone != JwTone.Neutral -> JwTagStyle.Tinted
            else -> JwTagStyle.Outlined
        },
        onClick = onToggle,
    )
}

@Composable
private fun EntryList(
    shownEntries: List<ListedSoilEntry>,
    isGroupedByKind: Boolean,
    selectedHandle: String?,
    agentNowEpochSeconds: Long,
    onSelect: (String) -> Unit,
) {
    val rows = if (isGroupedByKind) {
        shownEntries.groupBy { it.entry.kind }.flatMap { (kind, group) -> listOf(EntryListRow.Header(kind, group.size)) + group.map(EntryListRow::Entry) }
    } else {
        shownEntries.map(EntryListRow::Entry)
    }
    val listState = rememberLazyListState()
    val focusRequester = remember(calculation = ::FocusRequester)
    fun moveSelection(delta: Int) {
        val current = shownEntries.indexOfFirst { it.entry.handle == selectedHandle }
        val next = shownEntries.getOrNull(if (current < 0) 0 else (current + delta).coerceIn(0, shownEntries.lastIndex)) ?: return
        onSelect(next.entry.handle)
        listState.requestRevealItem(rows.indexOfFirst { it.key == next.entry.handle })
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
                if (keyEvent.type == KeyEventType.KeyDown && delta != 0) moveSelection(delta)
                delta != 0
            },
    ) {
        items(rows, key = EntryListRow::key) { row ->
            when (row) {
                is EntryListRow.Header -> JwSectionHeader(title = row.kind.pluralLabel, count = row.count)

                is EntryListRow.Entry -> EntryRow(
                    listed = row.listed,
                    isSelected = row.listed.entry.handle == selectedHandle,
                    agentNowEpochSeconds = agentNowEpochSeconds,
                    onClick = {
                        focusRequester.requestFocus()
                        onSelect(row.listed.entry.handle)
                    },
                )
            }
        }
    }
}

/** One entry on one line: its status, id and what sets it apart, and when Soil last updated it. */
@Composable
private fun EntryRow(listed: ListedSoilEntry, isSelected: Boolean, agentNowEpochSeconds: Long, onClick: () -> Unit) {
    val state = listed.entry.state
    JwListItem(selected = isSelected, onClick = onClick, muted = listed.isGone) {
        JwStatusDot(tone = state.status.tone, filled = !listed.isGone)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            JwText(text = listed.entry.id.namespace, style = JwTheme.textStyles.code, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f, fill = false))
            if (listed.entry.id.tags.isNotEmpty()) {
                JwText(text = listed.entry.id.tags.joinToString(prefix = "[", postfix = "]"), style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
        }
        badgesOf(listed, agentNowEpochSeconds).filterNot(SoilEntryBadge::isRoutine).forEach { JwTag(text = it.text, tone = it.tone, style = JwTagStyle.Tinted) }
        val updatedAt = maxOf(state.replyUpdatedAt, state.errorUpdatedAt)
        if (updatedAt != 0L) {
            JwText(text = describeEpochSeconds(minOf(updatedAt, agentNowEpochSeconds), agentNowEpochSeconds, whenZero = ""), style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary, maxLines = 1)
        }
    }
}
