package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.net.URLDecoder

@Composable
internal fun TrafficTab(
    transactions: List<HttpTransaction>,
    onClear: () -> Unit,
    onCreateMock: (HttpTransaction) -> Unit,
) {
    var selectedTxId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val visible = remember(transactions, query) {
        val matched = if (query.isBlank()) {
            transactions
        } else {
            transactions.filter { tx ->
                tx.request.url.contains(query, ignoreCase = true) ||
                    tx.request.method.contains(query, ignoreCase = true) ||
                    tx.response?.statusCode?.toString()?.contains(query) == true
            }
        }
        matched.asReversed()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun moveSelection(delta: Int) {
        if (visible.isEmpty()) return
        val current = visible.indexOfFirst { it.txId == selectedTxId }
        val next = (if (current < 0) 0 else current + delta).coerceIn(0, visible.lastIndex)
        selectedTxId = visible[next].txId
        scope.launch { listState.animateScrollToItem(next) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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
            text = "${visible.size} / ${transactions.size} requests",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider()
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionDown -> {
                                    moveSelection(1)
                                    true
                                }

                                Key.DirectionUp -> {
                                    moveSelection(-1)
                                    true
                                }

                                else -> false
                            }
                        }
                    },
            ) {
                items(visible, key = { it.txId }) { tx ->
                    TransactionRow(
                        tx = tx,
                        selected = tx.txId == selectedTxId,
                        onClick = { selectedTxId = tx.txId },
                    )
                    HorizontalDivider()
                }
            }
            VerticalDivider()
            Column(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                val tx = transactions.firstOrNull { it.txId == selectedTxId }
                if (tx == null) {
                    Text(
                        text = "Select a request to see details",
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    TransactionDetail(tx = tx, onCreateMock = { onCreateMock(tx) })
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: HttpTransaction, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusBadge(tx)
        Text(
            text = tx.request.method,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = tx.request.url,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (tx.response?.fromMock == true) {
            MockChip()
        }
        tx.response?.let {
            Text(
                text = "${it.durationMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun StatusBadge(tx: HttpTransaction) {
    val (label, color) = when {
        tx.failure != null -> "ERR" to MaterialTheme.colorScheme.error
        tx.response == null -> "···" to MaterialTheme.colorScheme.outline
        tx.response.statusCode in 200..299 -> tx.response.statusCode.toString() to Color(0xFF2E7D32)
        tx.response.statusCode in 300..399 -> tx.response.statusCode.toString() to Color(0xFF1565C0)
        tx.response.statusCode >= 400 -> tx.response.statusCode.toString() to MaterialTheme.colorScheme.error
        else -> tx.response.statusCode.toString() to MaterialTheme.colorScheme.onSurface
    }
    Pill(label = label, color = color)
}

@Composable
private fun MockChip() {
    Pill(label = "MOCK", color = Color(0xFF8E24AA))
}

@Composable
private fun Pill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TransactionDetail(tx: HttpTransaction, onCreateMock: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBadge(tx)
            Text(
                text = tx.request.method,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
            if (tx.response?.fromMock == true) {
                MockChip()
            }
            Spacer(Modifier.weight(1f))
            if (tx.response != null) {
                OutlinedButton(onCreateMock) { Text("Mock this") }
            }
        }
        Text(
            text = tx.request.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Request")
        QueryParamBlock(tx.request.url)
        HeaderBlock(tx.request.headers)
        BodyBlock(label = "body", body = tx.request.body, truncated = tx.request.bodyTruncated)

        SectionTitle("Response")
        when {
            tx.failure != null -> Text(
                text = "Failed: ${tx.failure.message}",
                color = MaterialTheme.colorScheme.error,
            )

            tx.response != null -> {
                Text(
                    text = "${tx.response.statusCode} ${tx.response.statusDescription}  •  ${tx.response.durationMs}ms",
                    style = MaterialTheme.typography.bodyMedium,
                )
                HeaderBlock(tx.response.headers)
                BodyBlock(label = "body", body = tx.response.body, truncated = tx.response.bodyTruncated)
            }

            else -> Text(
                text = "Pending…",
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun QueryParamBlock(url: String) {
    val params = remember(url) { parseQueryParams(url) }
    if (params.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Query",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        params.forEach { (key, value) ->
            Text(
                text = "$key = $value",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun HeaderBlock(headers: Map<String, List<String>>) {
    if (headers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        headers.forEach { (key, values) ->
            Text(
                text = "$key: ${values.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
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
