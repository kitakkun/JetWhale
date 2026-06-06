package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(mockingEnabled, onToggleMocking)
            Text("Mocking enabled", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                onChanged(rules + MockRule(id = UUID.randomUUID().toString(), name = "New rule", matcher = MockMatcher(urlPattern = ""), response = MockResponseSpec()))
            }) { Text("Add rule") }
        }
        HorizontalDivider()
        if (rules.isEmpty()) {
            Text("No mock rules. Add one to override matching responses.", color = MaterialTheme.colorScheme.outline)
        }
        rules.forEach { rule ->
            MockRuleCard(
                rule = rule,
                onChange = { updated -> onChanged(rules.map { if (it.id == updated.id) updated else it }) },
                onDelete = { onChanged(rules.filterNot { it.id == rule.id }) },
            )
        }
    }
}

@Composable
private fun MockRuleCard(rule: MockRule, onChange: (MockRule) -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(rule.enabled, { onChange(rule.copy(enabled = it)) })
                OutlinedTextField(rule.name, { onChange(rule.copy(name = it)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onDelete) { Text("Delete") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MethodDropdown(rule.matcher.method, { onChange(rule.copy(matcher = rule.matcher.copy(method = it))) }, Modifier.width(150.dp))
                OutlinedTextField(rule.matcher.urlPattern, { onChange(rule.copy(matcher = rule.matcher.copy(urlPattern = it))) }, label = { Text("URL pattern") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MockMatchType.entries.forEach { type ->
                    FilterChip(
                        selected = rule.matcher.matchType == type,
                        onClick = { onChange(rule.copy(matcher = rule.matcher.copy(matchType = type))) },
                        label = { Text(type.name) },
                    )
                }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rule.response.statusCode.toString(), { v -> onChange(rule.copy(response = rule.response.copy(statusCode = v.toIntOrNull() ?: rule.response.statusCode))) }, label = { Text("Status") }, singleLine = true, modifier = Modifier.width(110.dp))
                OutlinedTextField(rule.response.delayMs.toString(), { v -> onChange(rule.copy(response = rule.response.copy(delayMs = v.toLongOrNull() ?: rule.response.delayMs))) }, label = { Text("Delay ms") }, singleLine = true, modifier = Modifier.width(120.dp))
                OutlinedTextField(rule.response.headers["Content-Type"].orEmpty(), { v -> onChange(rule.copy(response = rule.response.copy(headers = if (v.isBlank()) rule.response.headers - "Content-Type" else rule.response.headers + ("Content-Type" to v)))) }, label = { Text("Content-Type") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(rule.response.body, { onChange(rule.copy(response = rule.response.copy(body = it))) }, label = { Text("Response body") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val httpMethods = listOf("ANY", "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodDropdown(method: String?, onSelect: (String?) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val current = method?.takeIf { it.isNotBlank() } ?: "ANY"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
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
