package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwStatusDot
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTooltip
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionPolicy
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus

/** Fits `+1.2s`, the offset of an event from the run it followed. */
private val OffsetColumnWidth = 52.dp

/** Fits `00:00:00.000`. */
private val TimeOfDayColumnWidth = 92.dp

/**
 * The selected entry: what its state means in words, the actions that apply, what a mutation's run
 * set off and the entry's latest events, then its state field by field, options and value.
 *
 * @param followUps For a mutation, what other entries did right after its last run; null when that
 *   run's end is not in the timeline, as for every other kind of entry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SoilEntryDetailPane(
    listed: ListedSoilEntry,
    notes: List<SoilEntryNote>,
    recentEvents: List<SoilEvent>,
    followUps: SoilMutationFollowUps?,
    value: SoilValueLoad?,
    agentNowEpochSeconds: Long,
    timeOfDayFormatter: TimeOfDayFormatter,
    onRunAction: (SoilEntryAction) -> Unit,
    onReloadValue: () -> Unit,
    onSelectEvent: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = listed.entry
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(JwSpacing.large),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            JwText(text = entry.id.label, style = JwTheme.textStyles.title)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall), verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
                JwTag(text = entry.kind.label)
                badgesOf(listed, agentNowEpochSeconds).forEach { JwTag(text = it.text, tone = it.tone, style = JwTagStyle.Tinted) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            notes.forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
                    JwStatusDot(tone = note.tone, modifier = Modifier.padding(top = JwSpacing.small))
                    JwText(text = note.text)
                }
            }
        }
        if (!listed.isGone && entry.kind != SoilEntryKind.MUTATION) EntryActions(listed = listed, onRunAction = onRunAction)
        if (followUps != null && followUps.events.isNotEmpty()) {
            Section(title = "What followed the last run", trailing = null) {
                followUps.events.forEach { event ->
                    EventLine(
                        time = "+${describeDurationMillis(event.atEpochMillis - followUps.runEnd.atEpochMillis)}",
                        timeWidth = OffsetColumnWidth,
                        kind = event.kind,
                        description = listOfNotNull(event.entryId.label, event.durationMillis?.let(::describeDurationMillis), event.detail).joinToString("  ·  "),
                        onClick = { onSelectEvent(event.sequence) },
                    )
                }
            }
        }
        Section(title = "Value", trailing = { JwButton(text = "Reload", onClick = onReloadValue, style = JwButtonStyle.Text, enabled = !listed.isGone) }) {
            SoilValueView(value)
        }
        if (recentEvents.isNotEmpty()) {
            Section(title = "Recent events", trailing = null) {
                recentEvents.forEach { event ->
                    EventLine(
                        time = timeOfDayFormatter.formatTimeOfDay(event.atEpochMillis),
                        timeWidth = TimeOfDayColumnWidth,
                        kind = event.kind,
                        description = listOfNotNull(event.durationMillis?.let(::describeDurationMillis), event.detail).joinToString("  ·  "),
                        onClick = { onSelectEvent(event.sequence) },
                    )
                }
            }
        }
        Section(title = "State", trailing = null) {
            stateRowsOf(entry.state, agentNowEpochSeconds).forEach { (key, text) -> JwKeyValueRow(key = key, value = text) }
        }
        entry.state.error?.let { error ->
            Section(title = "Error", trailing = null) {
                JwKeyValueRow(key = "Class", value = error.className, monospace = true)
                JwKeyValueRow(key = "Message", value = error.message ?: "none")
            }
        }
        if (entry.options.isNotEmpty()) {
            Section(title = "Options", trailing = null) {
                entry.options.forEach { (key, text) -> JwKeyValueRow(key = key, value = text, monospace = true) }
            }
        }
        Section(title = "Id", trailing = null) {
            JwKeyValueRow(key = "Class", value = entry.id.className)
            JwKeyValueRow(key = "Namespace", value = entry.id.namespace, monospace = true)
            JwKeyValueRow(key = "Tags", value = entry.id.tags.joinToString().ifEmpty { "none" }, monospace = true)
            JwKeyValueRow(key = "Location", value = entry.location.label)
        }
    }
}

/** Every action, each disabled with the reason when it does not apply to the entry. */
@Composable
private fun EntryActions(listed: ListedSoilEntry, onRunAction: (SoilEntryAction) -> Unit) {
    val refusals = SoilEntryAction.entries.associateWith { SoilEntryActionPolicy.refusalOf(listed.entry, it) }
    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            refusals.forEach { (action, refusal) ->
                JwTooltip(text = refusal) {
                    JwButton(
                        text = action.label,
                        onClick = { onRunAction(action) },
                        enabled = refusal == null,
                        tone = if (action == SoilEntryAction.REMOVE_INACTIVE) JwTone.Error else JwTone.Accent,
                    )
                }
            }
        }
        refusals.forEach { (action, refusal) ->
            if (refusal != null) JwText(text = "${action.label}: $refusal", style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary)
        }
    }
}

/** An event in the detail pane: when, what happened, and the rest in [description]. */
@Composable
private fun EventLine(time: String, timeWidth: Dp, kind: SoilEventKind, description: String, onClick: () -> Unit) {
    JwListItem(selected = false, onClick = onClick) {
        JwText(text = time, style = JwTheme.textStyles.code, color = JwTheme.colors.textSecondary, modifier = Modifier.width(timeWidth))
        JwTag(text = kind.label, tone = kind.tone, style = JwTagStyle.Tinted)
        JwText(text = description, style = JwTheme.textStyles.bodySmall, color = JwTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Section(title: String, trailing: (@Composable RowScope.() -> Unit)?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
        JwSectionHeader(title = title, contentPadding = PaddingValues(), trailing = trailing)
        content()
    }
}

/** The state's fields under Soil's own names, timestamps relative to the app's clock. */
private fun stateRowsOf(state: SoilEntryState, agentNowEpochSeconds: Long): List<Pair<String, String>> {
    fun time(epochSeconds: Long, whenZero: String) = describeEpochSeconds(epochSeconds, agentNowEpochSeconds, whenZero) + if (epochSeconds == 0L) "" else "  ·  $epochSeconds"
    return buildList {
        add("status" to state.status.label)
        if (state is SoilEntryState.Query) {
            add(
                "fetchStatus" to when (val fetchStatus = state.fetchStatus) {
                    is SoilFetchStatus.Idle -> "Idle"
                    is SoilFetchStatus.Fetching -> if (fetchStatus.isValidating) "Fetching (validating)" else "Fetching"
                    is SoilFetchStatus.Paused -> "Paused until ${time(fetchStatus.unpauseAt, whenZero = "0")}"
                },
            )
        }
        add("reply" to if (state.hasReply) "present" else "none")
        add("replyUpdatedAt" to time(state.replyUpdatedAt, whenZero = if (state.hasReply) "0 (initial or preloaded data)" else "never"))
        add("errorUpdatedAt" to time(state.errorUpdatedAt, whenZero = "never"))
        when (state) {
            is SoilEntryState.Query -> {
                add("staleAt" to time(state.staleAt, whenZero = if (state.hasReply) "0 (stale from the start)" else "never"))
                add("isInvalidated" to state.isInvalidated.toString())
            }

            is SoilEntryState.Mutation -> {
                add("mutatedCount" to state.mutatedCount.toString())
                add("submittedAt" to time(state.submittedAt, whenZero = "never"))
            }

            is SoilEntryState.Subscription -> add("restartedAt" to time(state.restartedAt, whenZero = "never"))
        }
    }
}
