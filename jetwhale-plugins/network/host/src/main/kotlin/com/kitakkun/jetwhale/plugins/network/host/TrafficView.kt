package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwColumnOverflow
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwSplitPaneState
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import kotlinx.coroutines.launch

private val ListMinWidth = 240.dp
private val DetailMinWidth = 280.dp

/** Fits "DELETE" so the URL column starts at the same x on every row. */
private val MethodColumnWidth = 44.dp

/** Fits a three-digit status so the method column lines up. */
private val StatusTagWidth = 36.dp

/** Room for the MOCK tag. */
private val MockColumnWidth = 44.dp

/** Fits "1234ms". */
private val DurationColumnWidth = 52.dp

@Composable
internal fun TrafficTab(
    transactions: List<HttpTransaction>,
    selectedTxId: String?,
    splitPaneState: JwSplitPaneState,
    onSelectTx: (String) -> Unit,
    onClear: () -> Unit,
    onCreateMock: (HttpTransaction) -> Unit,
    modifier: Modifier = Modifier,
) {
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

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(JwSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        ) {
            JwSearchField(
                value = query,
                onValueChange = { query = it },
                clearLabel = "Clear filter",
                placeholder = "Filter by URL, method or status",
                modifier = Modifier.weight(1f),
            )
            JwText(
                text = "${visible.size} / ${transactions.size}",
                style = JwTheme.textStyles.labelSmall,
                color = JwTheme.colors.textSecondary,
            )
            JwButton(text = "Clear", onClick = onClear)
        }
        JwHorizontalDivider()
        JwSplitPane(
            modifier = Modifier.fillMaxSize(),
            state = splitPaneState,
            firstMinSize = ListMinWidth,
            secondMinSize = DetailMinWidth,
            first = {
                TrafficList(
                    transactions = visible,
                    selectedTxId = selectedTxId,
                    onSelectTx = onSelectTx,
                )
            },
            second = {
                TrafficDetailPane(
                    transaction = transactions.firstOrNull { it.txId == selectedTxId },
                    onCreateMock = onCreateMock,
                )
            },
        )
    }
}

@Composable
private fun TrafficList(
    transactions: List<HttpTransaction>,
    selectedTxId: String?,
    onSelectTx: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun moveSelection(delta: Int) {
        if (transactions.isEmpty()) return
        val current = transactions.indexOfFirst { it.txId == selectedTxId }
        val next = (if (current < 0) 0 else current + delta).coerceIn(0, transactions.lastIndex)
        onSelectTx(transactions[next].txId)
        scope.launch { listState.animateScrollToItem(next) }
    }

    JwTable(
        items = transactions,
        columns = rememberTrafficColumns(),
        key = HttpTransaction::txId,
        isSelected = { it.txId == selectedTxId },
        onClick = { onSelectTx(it.txId) },
        state = listState,
        modifier = modifier
            .fillMaxSize()
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
    )
}

@Composable
private fun rememberTrafficColumns(): List<JwTableColumn<HttpTransaction>> {
    // Read outside remember: the column list is built once, and a theme read is a composable call.
    val urlStyle = JwTheme.textStyles.bodySmall
    return remember(urlStyle) {
        listOf(
            JwTableColumn<HttpTransaction>(header = "Status", width = JwColumnWidth.Fixed(StatusTagWidth)) { StatusBadge(it) },
            JwTableColumn(header = "Method", width = JwColumnWidth.Fixed(MethodColumnWidth)) {
                JwText(text = it.request.method, style = JwTheme.textStyles.label)
            },
            // The list pane is narrow, so long URLs are read by scrolling the text sideways rather
            // than by selecting the row.
            JwTableColumn.text(
                header = "URL",
                width = JwColumnWidth.Weight(1f),
                overflow = JwColumnOverflow.Scroll,
                style = urlStyle,
            ) { it.request.url },
            JwTableColumn(header = "", width = JwColumnWidth.Fixed(MockColumnWidth)) {
                if (it.response?.fromMock == true) MockChip()
            },
            JwTableColumn(header = "Time", width = JwColumnWidth.Fixed(DurationColumnWidth), alignment = Alignment.End) {
                it.response?.let { response ->
                    JwText(
                        text = "${response.durationMs}ms",
                        style = JwTheme.textStyles.labelSmall,
                        color = JwTheme.colors.textSecondary,
                    )
                }
            },
        )
    }
}

@Composable
internal fun StatusBadge(tx: HttpTransaction) {
    val (label, tone) = when {
        tx.failure != null -> "ERR" to JwTone.Error
        tx.response == null -> "···" to JwTone.Neutral
        tx.response.statusCode in 200..299 -> tx.response.statusCode.toString() to JwTone.Success
        tx.response.statusCode in 300..399 -> tx.response.statusCode.toString() to JwTone.Info
        tx.response.statusCode >= 400 -> tx.response.statusCode.toString() to JwTone.Error
        else -> tx.response.statusCode.toString() to JwTone.Neutral
    }
    JwTag(text = label, tone = tone, style = JwTagStyle.Tinted, modifier = Modifier.width(StatusTagWidth))
}

@Composable
internal fun MockChip() {
    JwTag(text = "MOCK", tone = JwTone.Accent, style = JwTagStyle.Tinted)
}
