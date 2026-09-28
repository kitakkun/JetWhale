package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import kotlinx.serialization.json.Json

private val PrettyJson = Json { prettyPrint = true }

/** A finished run's outcome, how long it took, and what it returned or why it failed. */
@Composable
internal fun RunResult(result: ActionResult) {
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

private fun ActionOutcome.tone(): JwTone = when (this) {
    ActionOutcome.SUCCESS -> JwTone.Success
    ActionOutcome.FAILURE -> JwTone.Error
    ActionOutcome.TIMEOUT, ActionOutcome.CANCELLED -> JwTone.Warning
}

/** Whether the run is still going or how it ended. */
@Composable
internal fun RunRecord.StatusTag() {
    val result = result
    JwTag(text = result?.outcome?.name ?: "RUNNING", tone = result?.outcome?.tone() ?: JwTone.Info)
}

internal fun RunRecord.originLabel(): String = if (origin == RunOrigin.AI_AGENT) "AI agent" else "You"
