package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ArgumentsJson = Json { prettyPrint = true }

private val RunTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Every run of this session across actions, newest first, with the selected run's arguments and
 * result beside the list. Run again opens the action with its form filled from that run.
 */
@Composable
internal fun RunHistoryPane(
    runs: List<RunRecord>,
    selectedRunId: String?,
    onSelect: (runId: String) -> Unit,
    onRunAgain: (runId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (runs.isEmpty()) {
            JwEmptyState(title = "No runs yet", description = "Runs you or an AI agent start in this session appear here.")
        } else {
            RunHistorySplit(runs = runs, selectedRunId = selectedRunId, onSelect = onSelect, onRunAgain = onRunAgain)
        }
    }
}

@Composable
private fun RunHistorySplit(
    runs: List<RunRecord>,
    selectedRunId: String?,
    onSelect: (runId: String) -> Unit,
    onRunAgain: (runId: String) -> Unit,
) {
    val selected = runs.firstOrNull { it.runId == selectedRunId }
    JwSplitPane(
        first = {
            LazyColumn(Modifier.fillMaxSize()) {
                items(runs, key = RunRecord::runId) { run ->
                    JwListItem(selected = run.runId == selectedRunId, onClick = { onSelect(run.runId) }) {
                        JwText(text = run.startedAtText(), style = JwTheme.textStyles.code, color = JwTheme.colors.textSecondary)
                        Column(Modifier.weight(1f)) {
                            JwText(text = run.title, maxLines = 1)
                            JwText(text = run.summary(), style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary, maxLines = 1)
                        }
                        JwText(text = run.originLabel(), style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
                        run.StatusTag()
                    }
                }
            }
        },
        second = {
            Box(Modifier.fillMaxSize()) {
                if (selected == null) {
                    JwEmptyState(title = "Nothing selected", description = "Pick a run to see its arguments and result.")
                } else {
                    RunDetail(run = selected, onRunAgain = { onRunAgain(selected.runId) })
                }
            }
        },
    )
}

@Composable
private fun RunDetail(run: RunRecord, onRunAgain: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(JwSpacing.large),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            JwText(text = run.title, style = JwTheme.textStyles.title, modifier = Modifier.weight(1f, fill = false))
            run.StatusTag()
        }
        JwText(text = "${run.startedAtText()} · ${run.originLabel()}", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
        JwButton(text = "Run again", style = JwButtonStyle.Primary, onClick = onRunAgain)
        JwSectionHeader(title = "Arguments", contentPadding = PaddingValues())
        JwCodeBlock(
            text = if (run.arguments.isEmpty()) "no arguments" else ArgumentsJson.encodeToString(run.arguments),
            copyLabel = "Copy arguments",
            modifier = Modifier.fillMaxWidth(),
        )
        JwSectionHeader(title = "Result", contentPadding = PaddingValues())
        val result = run.result
        if (result == null) JwText(text = "Still running.", color = JwTheme.colors.textSecondary) else RunResult(result)
    }
}

private fun RunRecord.startedAtText(): String = RunTimeFormat.format(Instant.ofEpochMilli(startedAtMillis).atZone(ZoneId.systemDefault()))

/** The result or the error on one line, for the list; the detail shows it in full. */
private fun RunRecord.summary(): String {
    val result = result ?: return "running…"
    val full = listOfNotNull(result.error, result.text, result.json?.toString()).firstOrNull() ?: return "no result"
    return full.lineSequence().first()
}
