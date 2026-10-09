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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTooltip
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionPolicy
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus

/**
 * The selected entry: its id, the actions that apply to it, its state field by field with times
 * relative to the app's clock, its options, and its value.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SoilEntryDetailPane(
    listed: ListedSoilEntry,
    value: SoilValueLoad?,
    agentNowEpochSeconds: Long,
    onRunAction: (SoilEntryAction) -> Unit,
    onReloadValue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = listed.entry
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(JwSpacing.large),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            JwText(text = entry.id.namespace, style = JwTheme.textStyles.title)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall), verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
                JwTag(text = entry.kind.label)
                badgesOf(listed, agentNowEpochSeconds).forEach { JwTag(text = it.text, tone = it.tone, style = JwTagStyle.Tinted) }
            }
        }
        if (listed.isGone) {
            JwText(text = "Soil dropped this mutation; this is the last state the app reported.", color = JwTheme.colors.textSecondary)
        } else {
            EntryActions(listed = listed, onRunAction = onRunAction)
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
        Section(title = "Value", trailing = { JwButton(text = "Reload", onClick = onReloadValue, style = JwButtonStyle.Text, enabled = !listed.isGone) }) {
            SoilValueView(value)
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
                add("staleAt" to time(state.staleAt, whenZero = "0 (stale from the start)"))
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
