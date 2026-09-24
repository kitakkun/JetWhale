package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val PrettyJson = Json { prettyPrint = true }

/** How many of the action's earlier runs are listed under its latest result. */
private const val LISTED_RUNS = 10

@Composable
internal fun ActionDetailPane(
    action: ActionDescriptor,
    options: Map<String, List<String>>,
    rememberedArguments: JsonObject?,
    runs: List<RunRecord>,
    onRun: (arguments: JsonObject, confirmedDestructive: Boolean) -> Unit,
    onCancel: (runId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberPersistent loads the stored arguments after the first frame; keying on them lets the
    // form pick them up when they arrive instead of keeping the blank start.
    var values by remember(action.id, rememberedArguments) { mutableStateOf(initialFormValues(action.parameters, rememberedArguments)) }
    var errors by remember(action.id) { mutableStateOf(emptyMap<String, String>()) }
    var confirming by remember(action.id) { mutableStateOf<JsonObject?>(null) }
    val running = runs.firstOrNull { it.result == null }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(JwSpacing.large),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        ActionHeader(action)
        action.parameters.forEach { parameter ->
            ParameterField(
                parameter = parameter,
                value = values[parameter.name].orEmpty(),
                error = errors[parameter.name],
                suggestions = options[parameter.name].orEmpty(),
                onValueChange = { values = values + (parameter.name to it) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            JwButton(
                text = if (action.destructive) "Run…" else "Run",
                style = JwButtonStyle.Primary,
                tone = if (action.destructive) JwTone.Error else JwTone.Accent,
                enabled = running == null,
                onClick = {
                    when (val built = buildArguments(action.parameters, values)) {
                        is FormArguments.Invalid -> errors = built.errors

                        is FormArguments.Valid -> {
                            errors = emptyMap()
                            if (action.destructive) confirming = built.arguments else onRun(built.arguments, false)
                        }
                    }
                },
            )
            running?.let { JwButton(text = "Cancel", onClick = { onCancel(it.runId) }) }
        }
        runs.firstNotNullOfOrNull(RunRecord::result)?.let { RunResult(it) }
        if (runs.isNotEmpty()) RunHistory(runs.take(LISTED_RUNS))
    }
    confirming?.let { arguments ->
        JwDialog(
            title = "Run ${action.title}?",
            closeLabel = "Cancel",
            onDismissRequest = { confirming = null },
            confirmButton = {
                JwButton(
                    text = "Run",
                    style = JwButtonStyle.Primary,
                    tone = JwTone.Error,
                    onClick = {
                        confirming = null
                        onRun(arguments, true)
                    },
                )
            },
            dismissButton = { JwButton(text = "Cancel", onClick = { confirming = null }) },
        ) {
            JwText(text = "This action is marked destructive: it changes or discards something in the app that cannot be restored.")
        }
    }
}

@Composable
private fun ActionHeader(action: ActionDescriptor) {
    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            JwText(text = action.title, style = JwTheme.textStyles.title, modifier = Modifier.weight(1f, fill = false))
            if (action.destructive) JwTag(text = "Destructive", tone = JwTone.Error)
            if (action.scoped) JwTag(text = "Screen", tone = JwTone.Info)
        }
        action.group?.let { JwText(text = it, style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary) }
        action.description?.let { JwText(text = it, color = JwTheme.colors.textSecondary) }
    }
}

@Composable
private fun RunResult(result: ActionResult) {
    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            JwTag(text = result.outcome.name, tone = result.outcome.tone())
            JwText(text = "${result.durationMillis} ms", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
        }
        result.error?.let { JwText(text = it, color = JwTone.Error.color) }
        result.text?.let { JwCodeBlock(text = it, wrap = true, copyLabel = "Copy result", modifier = Modifier.fillMaxWidth()) }
        result.json?.let { JwCodeBlock(text = PrettyJson.encodeToString(it), copyLabel = "Copy result", modifier = Modifier.fillMaxWidth()) }
        result.stackTrace?.let { JwCodeBlock(text = it, maxLines = 12, copyLabel = "Copy stack trace", modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun RunHistory(runs: List<RunRecord>) {
    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
        JwSectionHeader(title = "Runs", count = runs.size, contentPadding = PaddingValues())
        runs.forEach { run ->
            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                val result = run.result
                JwTag(text = result?.outcome?.name ?: "RUNNING", tone = result?.outcome?.tone() ?: JwTone.Info)
                JwText(text = if (run.origin == RunOrigin.AI_AGENT) "AI agent" else "You", style = JwTheme.textStyles.labelSmall)
                JwText(
                    text = run.arguments.takeIf { it.isNotEmpty() }?.toString() ?: "no arguments",
                    style = JwTheme.textStyles.code,
                    color = JwTheme.colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                result?.let { JwText(text = "${it.durationMillis} ms", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary) }
            }
        }
    }
}

private fun ActionOutcome.tone(): JwTone = when (this) {
    ActionOutcome.SUCCESS -> JwTone.Success
    ActionOutcome.FAILURE -> JwTone.Error
    ActionOutcome.TIMEOUT, ActionOutcome.CANCELLED -> JwTone.Warning
}
