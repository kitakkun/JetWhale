package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.network.protocol.AppliedNetworkCondition
import com.kitakkun.jetwhale.plugins.network.protocol.InjectedFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkCondition
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import java.util.UUID

@Composable
internal fun ConditionsTab(
    rules: List<NetworkConditionRule>,
    onChanged: (List<NetworkConditionRule>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<NetworkConditionRule?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(JwSpacing.large),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.large),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
            JwText(text = "Presets", style = JwTheme.textStyles.label)
            // A preset replaces the set: two catch-all rules would only ever apply the first.
            NetworkConditionPreset.entries.forEach { preset ->
                JwButton(text = preset.label, onClick = { onChanged(listOf(preset.toRule())) })
            }
            Spacer(Modifier.weight(1f))
            if (rules.isNotEmpty()) JwButton(text = "Clear all", onClick = { onChanged(emptyList()) }, style = JwButtonStyle.Text, tone = JwTone.Error)
            JwButton(text = "Add rule", onClick = { editing = blankConditionRule() }, style = JwButtonStyle.Primary)
        }
        JwText(
            text = if (rules.isEmpty()) {
                "No network conditions. Requests reach the network as they normally would."
            } else {
                "The first enabled rule that matches a request applies. Conditions last until cleared or until the app disconnects."
            },
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
        rules.forEach { rule ->
            ConditionRuleRow(
                rule = rule,
                onToggle = { enabled -> onChanged(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }) },
                onEdit = { editing = rule },
                onDelete = { onChanged(rules.filterNot { it.id == rule.id }) },
            )
        }
    }

    editing?.let { rule ->
        ConditionRuleDialog(
            initial = rule,
            onDismiss = { editing = null },
            onSave = { saved ->
                onChanged(if (rules.any { it.id == saved.id }) rules.map { if (it.id == saved.id) saved else it } else rules + saved)
                editing = null
            },
        )
    }
}

@Composable
private fun ConditionRuleRow(
    rule: NetworkConditionRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwPanel(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.large)) {
            JwSwitch(rule.enabled, contentDescription = "Rule enabled", onCheckedChange = onToggle)
            Column(Modifier.weight(1f)) {
                JwText(text = rule.name.ifBlank { "(unnamed rule)" }, style = JwTheme.textStyles.label)
                JwText(
                    text = "${rule.matcher?.let { "${it.method ?: "ANY"} • ${it.matchType} '${it.urlPattern}'" } ?: "All requests"} → ${rule.condition.summary()}",
                    style = JwTheme.textStyles.code,
                    color = JwTheme.colors.textSecondary,
                )
            }
            JwButton(text = "Edit", onClick = onEdit, style = JwButtonStyle.Text)
            JwButton(text = "Delete", onClick = onDelete, style = JwButtonStyle.Text, tone = JwTone.Error)
        }
    }
}

@Composable
private fun ConditionRuleDialog(
    initial: NetworkConditionRule,
    onDismiss: () -> Unit,
    onSave: (NetworkConditionRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    JwDialog(
        onDismissRequest = onDismiss,
        closeLabel = "Close",
        title = "Network condition",
        modifier = modifier,
        confirmButton = {
            JwButton(
                text = "Save",
                onClick = { onSave(draft) },
                enabled = draft.matcher?.urlPattern?.isNotBlank() ?: true,
                style = JwButtonStyle.Primary,
            )
        },
        dismissButton = { JwButton(text = "Cancel", onClick = onDismiss, style = JwButtonStyle.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(JwSpacing.large)) {
                JwFormField(label = "Name") {
                    JwTextField(value = draft.name, onValueChange = { draft = draft.copy(name = it) }, modifier = Modifier.fillMaxWidth())
                }
                ConditionScopeFields(matcher = draft.matcher, onMatcherChange = { draft = draft.copy(matcher = it) })
                ConditionFields(condition = draft.condition, onConditionChange = { draft = draft.copy(condition = it) })
            }
        },
    )
}

/** Which requests the rule applies to: all of them, or those a URL pattern selects. */
@Composable
private fun ConditionScopeFields(matcher: MockMatcher?, onMatcherChange: (MockMatcher?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        JwSwitch(matcher == null, contentDescription = "All requests", onCheckedChange = { all -> onMatcherChange(if (all) null else MockMatcher(urlPattern = "")) })
        JwText(text = "All requests", style = JwTheme.textStyles.body)
    }
    if (matcher == null) return
    JwFormField(label = "URL pattern") {
        JwTextField(
            value = matcher.urlPattern,
            onValueChange = { onMatcherChange(matcher.copy(urlPattern = it)) },
            textStyle = JwTheme.textStyles.code,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ChoiceTags(options = MockMatchType.entries, selected = matcher.matchType, label = MockMatchType::name, onSelect = { onMatcherChange(matcher.copy(matchType = it)) })
}

@Composable
private fun ConditionFields(condition: NetworkCondition, onConditionChange: (NetworkCondition) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        JwSwitch(condition.offline, contentDescription = "Offline", onCheckedChange = { onConditionChange(condition.copy(offline = it)) })
        JwText(text = "Offline — every matching request fails at once", style = JwTheme.textStyles.body)
    }
    if (condition.offline) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium), verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        NumberField("Latency ms", condition.latencyMs, onValueChange = { onConditionChange(condition.copy(latencyMs = it ?: 0)) })
        NumberField("Jitter ms", condition.jitterMs, onValueChange = { onConditionChange(condition.copy(jitterMs = it ?: 0)) })
        NumberField("Download KB/s", condition.downloadBytesPerSecond?.div(BYTES_PER_KB), onValueChange = { onConditionChange(condition.copy(downloadBytesPerSecond = it?.times(BYTES_PER_KB))) })
        NumberField("Upload KB/s", condition.uploadBytesPerSecond?.div(BYTES_PER_KB), onValueChange = { onConditionChange(condition.copy(uploadBytesPerSecond = it?.times(BYTES_PER_KB))) })
        NumberField("Failure %", (condition.failureRate * PERCENT).toLong(), onValueChange = { onConditionChange(condition.copy(failureRate = ((it ?: 0) / PERCENT).coerceIn(0.0, 1.0))) })
        NumberField("Fail after ms", condition.failureAfterMs, onValueChange = { onConditionChange(condition.copy(failureAfterMs = it ?: 0)) })
    }
    if (condition.failureRate > 0.0) {
        JwFormField(label = "Injected failure") {
            ChoiceTags(options = InjectedFailure.entries, selected = condition.failure, label = InjectedFailure::name, onSelect = { onConditionChange(condition.copy(failure = it)) })
        }
    }
}

/** A whole-number field; blank reads as null, so an optional cap can be removed. */
@Composable
private fun NumberField(label: String, value: Long?, onValueChange: (Long?) -> Unit) {
    JwFormField(label = label, modifier = Modifier.width(NumberFieldWidth)) {
        JwTextField(
            value = value?.toString().orEmpty(),
            onValueChange = { text -> if (text.isBlank() || text.toLongOrNull() != null) onValueChange(text.toLongOrNull()) },
            textStyle = JwTheme.textStyles.code,
        )
    }
}

@Composable
private fun <T> ChoiceTags(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
        options.forEach { option ->
            val isSelected = option == selected
            JwTag(
                text = label(option),
                tone = if (isSelected) JwTone.Accent else JwTone.Neutral,
                style = if (isSelected) JwTagStyle.Filled else JwTagStyle.Outlined,
                onClick = { onSelect(option) },
            )
        }
    }
}

private fun NetworkCondition.summary(): String = if (offline) {
    "offline"
} else {
    listOfNotNull(
        "+${latencyMs}ms".takeIf { latencyMs > 0 }?.let { if (jitterMs > 0) "$it ±${jitterMs}ms" else it },
        downloadBytesPerSecond?.let { "↓ ${formatRate(it)}" },
        uploadBytesPerSecond?.let { "↑ ${formatRate(it)}" },
        "${(failureRate * PERCENT).toInt()}% $failure".takeIf { failureRate > 0.0 },
    ).joinToString(" • ").ifEmpty { "no change" }
}

/** What a condition did to one transaction, as the traffic view shows it. */
internal fun AppliedNetworkCondition.summary(): String = listOfNotNull(
    "offline".takeIf { offline },
    "+${addedLatencyMs}ms".takeIf { addedLatencyMs > 0 },
    downloadBytesPerSecond?.let { "↓ ${formatRate(it)}" },
    uploadBytesPerSecond?.let { "↑ ${formatRate(it)}" },
    injectedFailure?.let { "injected $it" },
).joinToString(" • ").ifEmpty { "no change" }

private fun formatRate(bytesPerSecond: Long): String = if (bytesPerSecond >= BYTES_PER_KB * BYTES_PER_KB) {
    "${bytesPerSecond / (BYTES_PER_KB * BYTES_PER_KB)} MB/s"
} else {
    "${bytesPerSecond / BYTES_PER_KB} KB/s"
}

private fun blankConditionRule(): NetworkConditionRule = NetworkConditionRule(
    id = UUID.randomUUID().toString(),
    name = "",
    enabled = true,
    matcher = null,
    condition = NetworkCondition(latencyMs = 500),
)

private const val BYTES_PER_KB = 1_000L
private const val PERCENT = 100.0

/** Six digits and a little room. */
private val NumberFieldWidth: Dp = 120.dp
