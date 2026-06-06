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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(mockingEnabled, onToggleMocking)
            Text(
                text = "Mocking enabled",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = { editing = blankRule() }) { Text("Add rule") }
        }
        if (rules.isEmpty()) {
            Text(
                text = "No mock rules. Add one to override matching responses.",
                color = MaterialTheme.colorScheme.outline,
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
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(rule.enabled, onToggle)
            Column(Modifier.weight(1f)) {
                Text(
                    text = rule.name.ifBlank { "(unnamed rule)" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${rule.matcher.method ?: "ANY"} • ${rule.matcher.matchType} '${rule.matcher.urlPattern}' → ${rule.response.statusCode}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(onEdit) { Text("Edit") }
            TextButton(onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun MockRuleDialog(initial: MockRule, onDismiss: () -> Unit, onSave: (MockRule) -> Unit) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mock rule") },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.matcher.urlPattern.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MethodDropdown(
                        method = draft.matcher.method,
                        onSelect = { draft = draft.copy(matcher = draft.matcher.copy(method = it)) },
                        modifier = Modifier.width(150.dp),
                    )
                    OutlinedTextField(
                        value = draft.matcher.urlPattern,
                        onValueChange = { draft = draft.copy(matcher = draft.matcher.copy(urlPattern = it)) },
                        label = { Text("URL pattern") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MockMatchType.entries.forEach { type ->
                        FilterChip(
                            selected = draft.matcher.matchType == type,
                            onClick = { draft = draft.copy(matcher = draft.matcher.copy(matchType = type)) },
                            label = { Text(type.name) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.response.statusCode.toString(),
                        onValueChange = { value ->
                            draft = draft.copy(
                                response = draft.response.copy(
                                    statusCode = value.toIntOrNull() ?: draft.response.statusCode,
                                ),
                            )
                        },
                        label = { Text("Status") },
                        singleLine = true,
                        modifier = Modifier.width(110.dp),
                    )
                    OutlinedTextField(
                        value = draft.response.delayMs.toString(),
                        onValueChange = { value ->
                            draft = draft.copy(
                                response = draft.response.copy(
                                    delayMs = value.toLongOrNull() ?: draft.response.delayMs,
                                ),
                            )
                        },
                        label = { Text("Delay ms") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    OutlinedTextField(
                        value = draft.response.headers["Content-Type"].orEmpty(),
                        onValueChange = { value ->
                            val headers = if (value.isBlank()) {
                                draft.response.headers - "Content-Type"
                            } else {
                                draft.response.headers + ("Content-Type" to value)
                            }
                            draft = draft.copy(response = draft.response.copy(headers = headers))
                        },
                        label = { Text("Content-Type") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = draft.response.body,
                    onValueChange = { draft = draft.copy(response = draft.response.copy(body = it)) },
                    label = { Text("Response body") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                )
            }
        },
    )
}

private val httpMethods = listOf("ANY", "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodDropdown(method: String?, onSelect: (String?) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val current = method?.takeIf { it.isNotBlank() } ?: "ANY"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text("Method") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            httpMethods.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onSelect(item.takeIf { it != "ANY" })
                        expanded = false
                    },
                )
            }
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
