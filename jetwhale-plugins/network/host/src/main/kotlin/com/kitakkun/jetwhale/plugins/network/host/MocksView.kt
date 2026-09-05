package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTypography
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import java.util.UUID

@Composable
internal fun MocksTab(
    rules: List<MockRule>,
    mockingEnabled: Boolean,
    onToggleMocking: (Boolean) -> Unit,
    onChanged: (List<MockRule>) -> Unit,
) {
    var editing by remember { mutableStateOf<MockRule?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(JwSpacing.large),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.large),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        ) {
            JwSwitch(mockingEnabled, onToggleMocking, contentDescription = "Mocking enabled")
            Text(
                text = "Mocking enabled",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            JwButton(text = "Add rule", onClick = { editing = blankRule() }, style = JwButtonStyle.Primary)
        }
        if (rules.isEmpty()) {
            Text(
                text = "No mock rules. Add one to override matching responses.",
                style = MaterialTheme.typography.bodySmall,
                color = JwTheme.colors.textSecondary,
            )
        }
        rules.forEach { rule ->
            MockRuleRow(
                rule = rule,
                onToggle = { enabled ->
                    onChanged(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                },
                onEdit = { editing = rule },
                onDelete = { onChanged(rules.filterNot { it.id == rule.id }) },
            )
        }
    }

    editing?.let { rule ->
        MockRuleDialog(
            initial = rule,
            onDismiss = { editing = null },
            onSave = { saved ->
                onChanged(rules.upsert(saved))
                editing = null
            },
        )
    }
}

@Composable
private fun MockRuleRow(
    rule: MockRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    JwPanel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.large),
        ) {
            JwSwitch(rule.enabled, onToggle, contentDescription = "Rule enabled")
            Column(Modifier.weight(1f)) {
                Text(
                    text = rule.name.ifBlank { "(unnamed rule)" },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "${rule.matcher.method ?: "ANY"} • ${rule.matcher.matchType} '${rule.matcher.urlPattern}' → ${rule.response.statusCode}",
                    style = JwTypography.code,
                    color = JwTheme.colors.textSecondary,
                )
            }
            JwButton(text = "Edit", onClick = onEdit, style = JwButtonStyle.Text)
            JwButton(text = "Delete", onClick = onDelete, style = JwButtonStyle.Text, tone = JwTone.Error)
        }
    }
}

@Composable
private fun MockRuleDialog(initial: MockRule, onDismiss: () -> Unit, onSave: (MockRule) -> Unit) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    JwDialog(
        onDismissRequest = onDismiss,
        closeLabel = "Close",
        title = "Mock rule",
        confirmButton = {
            JwButton(
                text = "Save",
                onClick = { onSave(draft) },
                enabled = draft.matcher.urlPattern.isNotBlank(),
                style = JwButtonStyle.Primary,
            )
        },
        dismissButton = { JwButton(text = "Cancel", onClick = onDismiss, style = JwButtonStyle.Text) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(JwSpacing.large),
            ) {
                JwFormField(label = "Name") {
                    JwTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                    JwFormField(label = "Method", modifier = Modifier.width(MethodFieldWidth)) {
                        MethodDropdown(
                            method = draft.matcher.method,
                            onSelect = { draft = draft.copy(matcher = draft.matcher.copy(method = it)) },
                        )
                    }
                    JwFormField(label = "URL pattern", modifier = Modifier.weight(1f)) {
                        JwTextField(
                            value = draft.matcher.urlPattern,
                            onValueChange = { draft = draft.copy(matcher = draft.matcher.copy(urlPattern = it)) },
                            textStyle = JwTypography.code,
                        )
                    }
                }
                JwFormField(label = "Match") {
                    Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
                        MockMatchType.entries.forEach { type ->
                            val selected = draft.matcher.matchType == type
                            JwTag(
                                text = type.name,
                                tone = if (selected) JwTone.Accent else JwTone.Neutral,
                                style = if (selected) JwTagStyle.Filled else JwTagStyle.Outlined,
                                onClick = { draft = draft.copy(matcher = draft.matcher.copy(matchType = type)) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                    JwFormField(label = "Status", modifier = Modifier.width(StatusFieldWidth)) {
                        JwTextField(
                            value = draft.response.statusCode.toString(),
                            onValueChange = { value ->
                                draft = draft.copy(
                                    response = draft.response.copy(
                                        statusCode = value.toIntOrNull() ?: draft.response.statusCode,
                                    ),
                                )
                            },
                            textStyle = JwTypography.code,
                        )
                    }
                    JwFormField(label = "Delay ms", modifier = Modifier.width(DelayFieldWidth)) {
                        JwTextField(
                            value = draft.response.delayMs.toString(),
                            onValueChange = { value ->
                                draft = draft.copy(
                                    response = draft.response.copy(
                                        delayMs = value.toLongOrNull() ?: draft.response.delayMs,
                                    ),
                                )
                            },
                            textStyle = JwTypography.code,
                        )
                    }
                    JwFormField(label = "Content-Type", modifier = Modifier.weight(1f)) {
                        JwTextField(
                            value = draft.response.headers["Content-Type"].orEmpty(),
                            onValueChange = { value ->
                                val headers = if (value.isBlank()) {
                                    draft.response.headers - "Content-Type"
                                } else {
                                    draft.response.headers + ("Content-Type" to value)
                                }
                                draft = draft.copy(response = draft.response.copy(headers = headers))
                            },
                            textStyle = JwTypography.code,
                        )
                    }
                }
                JwFormField(label = "Response body") {
                    JwTextField(
                        value = draft.response.body,
                        onValueChange = { draft = draft.copy(response = draft.response.copy(body = it)) },
                        singleLine = false,
                        textStyle = JwTypography.code,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = BodyEditorMinHeight),
                    )
                }
            }
        },
    )
}

/** Fits "OPTIONS" plus the chevron. */
private val MethodFieldWidth = 130.dp

/** Three digits. */
private val StatusFieldWidth = 80.dp

/** Up to five digits of milliseconds. */
private val DelayFieldWidth = 90.dp

/** Enough lines to read a small JSON body without scrolling. */
private val BodyEditorMinHeight = 100.dp

private val httpMethods = listOf("ANY", "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

@Composable
private fun MethodDropdown(method: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = method?.takeIf { it.isNotBlank() } ?: "ANY"
    JwDropdownButton(
        text = current,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        httpMethods.forEach { item ->
            JwMenuItem(
                text = item,
                selected = item == current,
                onClick = {
                    onSelect(item.takeIf { it != "ANY" })
                    expanded = false
                },
            )
        }
    }
}

private fun blankRule(): MockRule = MockRule(
    id = UUID.randomUUID().toString(),
    name = "",
    enabled = true,
    matcher = MockMatcher(urlPattern = ""),
    response = MockResponseSpec(),
)

private fun List<MockRule>.upsert(rule: MockRule): List<MockRule> = if (any { it.id == rule.id }) {
    map { if (it.id == rule.id) rule else it }
} else {
    this + rule
}
