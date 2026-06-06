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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.URLDecoder
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
            0 -> TrafficTab(
                transactions = transactions,
                onClear = onClearTransactions,
                onCreateMock = { tx ->
                    tx.response?.let { response ->
                        onMockRulesChanged(mockRules + mockRuleFrom(tx, response))
                        selectedTab = 1
                    }
                },
            )

            else -> MocksTab(mockRules, mockingEnabled, onToggleMocking, onMockRulesChanged)
        }
    }
}

@Composable
private fun TrafficTab(
    transactions: List<HttpTransaction>,
    onClear: () -> Unit,
    onCreateMock: (HttpTransaction) -> Unit,
) {
    var selectedTxId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(transactions, query) {
        if (query.isBlank()) {
            transactions
        } else {
            transactions.filter { tx ->
                tx.request.url.contains(query, ignoreCase = true) ||
                    tx.request.method.contains(query, ignoreCase = true) ||
                    tx.response?.statusCode?.toString()?.contains(query) == true
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Filter (URL / method / status)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClear) { Text("Clear") }
        }
        Text(
            "${filtered.size} / ${transactions.size} requests",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider()
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                items(filtered.asReversed(), key = { it.txId }) { tx ->
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
                    TransactionDetail(tx, onCreateMock = { onCreateMock(tx) })
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
private fun TransactionDetail(tx: HttpTransaction, onCreateMock: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${tx.request.method} ${tx.request.url}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (tx.response != null) {
                OutlinedButton(onCreateMock) { Text("Mock this") }
            }
        }
        if (tx.response?.fromMock == true) {
            Text("Served from mock", color = Color(0xFF8E24AA), style = MaterialTheme.typography.labelMedium)
        }
        SectionTitle("Request")
        QueryParamBlock(tx.request.url)
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
private fun QueryParamBlock(url: String) {
    val params = remember(url) { parseQueryParams(url) }
    if (params.isEmpty()) return
    Column {
        Text("Query parameters", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        params.forEach { (key, value) ->
            Text("$key = $value", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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
    val json = remember(body) { runCatching { lenientJson.parseToJsonElement(body) }.getOrNull() }
    var treeMode by remember(body) { mutableStateOf(json != null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (json != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(treeMode, onClick = { treeMode = true }, label = { Text("Tree") })
                FilterChip(!treeMode, onClick = { treeMode = false }, label = { Text("JSON") })
            }
        }
        Card(Modifier.fillMaxWidth()) {
            if (json != null && treeMode) {
                Column(Modifier.padding(8.dp)) { JsonTreeNode(json) }
            } else {
                val text = json?.let { prettyJson.encodeToString(JsonElement.serializer(), it) } ?: body
                Text(
                    text + if (truncated) "\n… (truncated)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun JsonTreeNode(element: JsonElement, label: String? = null) {
    val prefix = label?.let { "$it: " } ?: ""
    when (element) {
        is JsonObject -> {
            var expanded by remember { mutableStateOf(true) }
            Text(
                "$prefix{${if (expanded) "" else "… ${element.size}"}}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            if (expanded) {
                Column(Modifier.padding(start = 14.dp)) {
                    element.forEach { (key, value) -> JsonTreeNode(value, key) }
                }
            }
        }

        is JsonArray -> {
            var expanded by remember { mutableStateOf(true) }
            Text(
                "$prefix[${if (expanded) "" else "… ${element.size}"}]",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            if (expanded) {
                Column(Modifier.padding(start = 14.dp)) {
                    element.forEachIndexed { index, value -> JsonTreeNode(value, index.toString()) }
                }
            }
        }

        else -> Text("$prefix$element", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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

private val lenientJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

private val prettyJson = Json {
    prettyPrint = true
    isLenient = true
}

private fun parseQueryParams(url: String): List<Pair<String, String>> {
    val query = url.substringAfter('?', "")
    if (query.isBlank()) return emptyList()
    return query.split('&').filter { it.isNotBlank() }.map { part ->
        val index = part.indexOf('=')
        if (index < 0) {
            urlDecode(part) to ""
        } else {
            urlDecode(part.substring(0, index)) to urlDecode(part.substring(index + 1))
        }
    }
}

private fun urlDecode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

private fun mockRuleFrom(tx: HttpTransaction, response: CapturedHttpResponse): MockRule {
    val contentType = response.headers.entries
        .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
        ?.value?.firstOrNull()
    return MockRule(
        id = UUID.randomUUID().toString(),
        name = "${tx.request.method} ${tx.request.url.substringBefore('?').takeLast(40)}",
        matcher = MockMatcher(
            method = tx.request.method,
            urlPattern = tx.request.url.substringBefore('?'),
            matchType = MockMatchType.EXACT,
        ),
        response = MockResponseSpec(
            statusCode = response.statusCode,
            headers = contentType?.let { mapOf("Content-Type" to it) }.orEmpty(),
            body = response.body.orEmpty(),
        ),
    )
}
