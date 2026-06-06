package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import java.util.UUID

@Composable
fun NetworkInspectorScreen(
    transactions: List<HttpTransaction>,
    mockRules: List<MockRule>,
    mockingEnabled: Boolean,
    onClearTransactions: () -> Unit,
    onToggleMocking: (Boolean) -> Unit,
    onMockRulesChanged: (List<MockRule>) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Traffic (${transactions.size})") })
            Tab(selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Mocks (${mockRules.size})") })
        }
        when (selectedTab) {
            0 -> TrafficTab(transactions, onClearTransactions)
            else -> MocksTab(mockRules, mockingEnabled, onToggleMocking, onMockRulesChanged)
        }
    }
}

@Composable
private fun TrafficTab(transactions: List<HttpTransaction>, onClear: () -> Unit) {
    var selectedTxId by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClear) { Text("Clear") }
            Text("${transactions.size} requests", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                items(transactions.asReversed(), key = { it.txId }) { tx ->
                    TransactionRow(tx, selected = tx.txId == selectedTxId) { selectedTxId = tx.txId }
                    HorizontalDivider()
                }
            }
            VerticalDivider()
            Column(Modifier.weight(1.4f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) {
                val tx = transactions.firstOrNull { it.txId == selectedTxId }
                if (tx == null) {
                    Text("Select a request to see details", color = MaterialTheme.colorScheme.outline)
                } else {
                    TransactionDetail(tx)
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: HttpTransaction, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusBadge(tx)
        Text(tx.request.method, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Text(
            tx.request.url,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        tx.response?.let { Text("${it.durationMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
    }
}

@Composable
private fun StatusBadge(tx: HttpTransaction) {
    val (label, color) = when {
        tx.failure != null -> "ERR" to MaterialTheme.colorScheme.error
        tx.response == null -> "…" to MaterialTheme.colorScheme.outline
        tx.response.statusCode in 200..299 -> tx.response.statusCode.toString() to Color(0xFF2E7D32)
        tx.response.statusCode in 300..399 -> tx.response.statusCode.toString() to Color(0xFF1565C0)
        tx.response.statusCode >= 400 -> tx.response.statusCode.toString() to MaterialTheme.colorScheme.error
        else -> tx.response.statusCode.toString() to MaterialTheme.colorScheme.onSurface
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (tx.response?.fromMock == true) Text("M", color = Color(0xFF8E24AA), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TransactionDetail(tx: HttpTransaction) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${tx.request.method} ${tx.request.url}", fontWeight = FontWeight.Bold)
        if (tx.response?.fromMock == true) {
            Text("Served from mock", color = Color(0xFF8E24AA), style = MaterialTheme.typography.labelMedium)
        }
        SectionTitle("Request")
        HeaderBlock(tx.request.headers)
        BodyBlock(tx.request.body, tx.request.bodyTruncated)

        SectionTitle("Response")
        when {
            tx.failure != null -> Text("Failed: ${tx.failure.message}", color = MaterialTheme.colorScheme.error)

            tx.response != null -> {
                Text("${tx.response.statusCode} ${tx.response.statusDescription}  •  ${tx.response.durationMs}ms")
                HeaderBlock(tx.response.headers)
                BodyBlock(tx.response.body, tx.response.bodyTruncated)
            }

            else -> Text("Pending…", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.width(0.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun HeaderBlock(headers: Map<String, List<String>>) {
    if (headers.isEmpty()) return
    Column {
        headers.forEach { (key, values) ->
            Text("$key: ${values.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun BodyBlock(body: String?, truncated: Boolean) {
    if (body.isNullOrEmpty()) return
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            body + if (truncated) "\n… (truncated)" else "",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(8.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------------------

@Composable
private fun MocksTab(
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
                OutlinedTextField(rule.matcher.method.orEmpty(), { onChange(rule.copy(matcher = rule.matcher.copy(method = it.ifBlank { null }))) }, label = { Text("Method (any)") }, singleLine = true, modifier = Modifier.width(120.dp))
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
