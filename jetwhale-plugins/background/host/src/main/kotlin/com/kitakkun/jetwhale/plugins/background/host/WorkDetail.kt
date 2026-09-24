package com.kitakkun.jetwhale.plugins.background.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkSourceInfo
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/** A cancellation the user has asked for and not yet confirmed. */
private data class PendingCancel(val target: CancelTarget, val description: String)

@Composable
internal fun WorkDetail(
    item: BackgroundWorkItem,
    source: WorkSourceInfo?,
    history: List<StateTransition>,
    actions: BackgroundWorkActions,
    modifier: Modifier = Modifier,
) {
    var pendingCancel by remember(item.key) { mutableStateOf<PendingCancel?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(JwSpacing.large), verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            JwText(text = shortName(item.name), style = JwTheme.textStyles.title, modifier = Modifier.weight(1f))
            JwButton(text = "Run now", onClick = { actions.runNow(item.key) }, enabled = item.canRunNow)
            JwButton(
                text = "Cancel…",
                onClick = { pendingCancel = PendingCancel(CancelTarget.ById(item.id), "this ${item.source} work") },
                tone = JwTone.Error,
                enabled = item.canCancel,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            StateTag(item.state)
            JwText(text = item.source, style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
        }
        workFactsOf(item).forEach { JwKeyValueRow(key = it.label, value = it.value, monospace = it.monospace, wrap = !it.monospace) }
        if (source?.supportsCancelByTag == true && item.tags.isNotEmpty()) {
            TagActions(tags = item.tags, onCancelTag = { tag -> pendingCancel = PendingCancel(CancelTarget.ByTag(tag), "all ${item.source} work tagged $tag") })
        }
        item.runNowHint?.let { hint ->
            JwSectionHeader(title = "Run it from outside the app")
            JwCodeBlock(text = hint, wrap = true, copyLabel = "Copy command")
        }
        DataSection(title = "Progress", data = item.progress)
        DataSection(title = "Output", data = item.output)
        DataSection(title = "Details", data = item.details)
        HistorySection(history)
    }
    pendingCancel?.let { cancel ->
        ConfirmDialog(
            title = "Cancel ${cancel.description}?",
            message = "The app's scheduler drops it; work already running is stopped. This cannot be undone.",
            confirmLabel = "Cancel work",
            onConfirm = {
                pendingCancel = null
                actions.cancel(item.source, cancel.target)
            },
            onDismiss = { pendingCancel = null },
        )
    }
}

/** One labelled line of the detail pane. [monospace] suits identifiers that are copied rather than read. */
internal data class WorkFact(val label: String, val value: String, val monospace: Boolean)

/** What the detail pane says about [item] besides its data maps, in reading order. */
internal fun workFactsOf(item: BackgroundWorkItem): List<WorkFact> = buildList {
    add(WorkFact("Class", item.name, monospace = true))
    add(WorkFact("Id", item.id, monospace = true))
    item.uniqueName?.let { add(WorkFact("Unique name", it, monospace = false)) }
    item.runAttemptCount?.let { add(WorkFact("Run attempts", it.toString(), monospace = false)) }
    item.nextRunEpochMillis?.let { add(WorkFact("Earliest run", DateTimeFormatterLong.format(Instant.ofEpochMilli(it)), monospace = false)) }
    item.periodMillis?.let { period ->
        val flex = item.flexMillis?.let { " (flex ${it.milliseconds})" }.orEmpty()
        add(WorkFact("Repeats every", "${period.milliseconds}$flex", monospace = false))
    }
    if (item.constraints.isNotEmpty()) add(WorkFact("Waits for", item.constraints.joinToString(), monospace = false))
    item.stopReason?.let { add(WorkFact("Last stopped", it, monospace = false)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagActions(tags: List<String>, onCancelTag: (String) -> Unit) {
    JwSectionHeader(title = "Cancel everything with a tag")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
        tags.forEach { tag -> JwTag(text = tag, onClick = { onCancelTag(tag) }) }
    }
}

@Composable
private fun DataSection(title: String, data: Map<String, String>) {
    if (data.isEmpty()) return
    JwSectionHeader(title = title, count = data.size)
    data.forEach { (key, value) -> JwKeyValueRow(key = key, value = value, monospace = true) }
}

@Composable
private fun HistorySection(history: List<StateTransition>) {
    if (history.isEmpty()) return
    JwSectionHeader(title = "Seen by the host", count = history.size)
    history.asReversed().forEach { transition ->
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            JwText(text = TimeFormatter.format(Instant.ofEpochMilli(transition.atEpochMillis)), style = JwTheme.textStyles.code)
            StateTag(transition.state)
        }
    }
}
