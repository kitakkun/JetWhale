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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import java.net.URLDecoder

@Composable
internal fun TrafficTab(
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
