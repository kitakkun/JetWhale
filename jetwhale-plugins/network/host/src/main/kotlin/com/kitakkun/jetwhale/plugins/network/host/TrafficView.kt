package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTypography
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import kotlinx.coroutines.launch
import java.net.URLDecoder

/** Storage key for the Traffic tab's list/detail split position. */
private const val SPLIT_POSITION_KEY = "traffic.splitPosition"
private const val DEFAULT_SPLIT_POSITION = 0.42f
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
    onSelectTx: (String) -> Unit,
    onClear: () -> Unit,
    onCreateMock: (HttpTransaction) -> Unit,
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun moveSelection(delta: Int) {
        if (visible.isEmpty()) return
        val current = visible.indexOfFirst { it.txId == selectedTxId }
        val next = (if (current < 0) 0 else current + delta).coerceIn(0, visible.lastIndex)
        onSelectTx(visible[next].txId)
        scope.launch { listState.animateScrollToItem(next) }
    }

    var storedSplitPosition by rememberPersistent(SPLIT_POSITION_KEY, DEFAULT_SPLIT_POSITION)
    val splitPaneState = rememberJwSplitPaneState(DEFAULT_SPLIT_POSITION)
    // rememberPersistent hydrates from disk asynchronously, i.e. after the split state has already
    // been constructed, so the two are mirrored in both directions rather than seeded once. Both are
    // backed by snapshot state with structural equality, so echoing an unchanged value back does not
    // re-emit and the mirroring settles immediately.
    LaunchedEffect(splitPaneState) {
        launch {
            snapshotFlow { storedSplitPosition }
                .collect { splitPaneState.fraction = it }
        }
        snapshotFlow { splitPaneState.fraction }
            .collect { storedSplitPosition = it }
    }

    val columns = remember {
        listOf(
            JwTableColumn<HttpTransaction>(header = "Status", width = JwColumnWidth.Fixed(StatusTagWidth)) { StatusBadge(it) },
            JwTableColumn(header = "Method", width = JwColumnWidth.Fixed(MethodColumnWidth)) {
                Text(text = it.request.method, style = MaterialTheme.typography.labelMedium)
            },
            JwTableColumn(header = "URL", width = JwColumnWidth.Weight(1f)) {
                Text(
                    text = it.request.url,
                    style = MaterialTheme.typography.bodySmall,
                    // The list pane is narrow, so long URLs are read by scrolling the text sideways
                    // rather than by selecting the row. maxLines = 1 plus softWrap = false keeps the
                    // URL on a single line inside the cell's fixed viewport.
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                )
            },
            JwTableColumn(header = "", width = JwColumnWidth.Fixed(MockColumnWidth)) {
                if (it.response?.fromMock == true) MockChip()
            },
            JwTableColumn(header = "Time", width = JwColumnWidth.Fixed(DurationColumnWidth), alignment = Alignment.End) {
                it.response?.let { response ->
                    Text(
                        text = "${response.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = JwTheme.colors.textSecondary,
                    )
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
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
            Text(
                text = "${visible.size} / ${transactions.size}",
                style = MaterialTheme.typography.labelSmall,
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
                JwTable(
                    items = visible,
                    columns = columns,
                    key = { it.txId },
                    isSelected = { it.txId == selectedTxId },
                    onClick = { onSelectTx(it.txId) },
                    state = listState,
                    modifier = Modifier
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
            },
            second = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(JwSpacing.large),
                ) {
                    val tx = transactions.firstOrNull { it.txId == selectedTxId }
                    if (tx == null) {
                        JwEmptyState(title = "Select a request to see details")
                    } else {
                        // Detail pane values (URL, headers, bodies) are read-only reference data
                        // developers frequently copy, so make the whole pane text-selectable.
                        SelectionContainer {
                            TransactionDetail(tx = tx, onCreateMock = { onCreateMock(tx) })
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun StatusBadge(tx: HttpTransaction) {
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
private fun MockChip() {
    JwTag(text = "MOCK", tone = JwTone.Accent, style = JwTagStyle.Tinted)
}

private enum class DetailTab(val title: String) {
    Body("Body"),
    Headers("Headers"),
    Query("Query"),
}

@Composable
private fun TransactionDetail(tx: HttpTransaction, onCreateMock: () -> Unit) {
    val queryParams = remember(tx.request.url) { parseQueryParams(tx.request.url) }
    val hasResponseBody = !tx.response?.body.isNullOrEmpty()
    // Key on hasResponseBody too: the body often arrives after the row is first selected (same
    // txId), and the default should follow it to Body once it exists.
    var selectedTab by remember(tx.txId, hasResponseBody) {
        mutableStateOf(if (hasResponseBody) DetailTab.Body else DetailTab.Headers)
    }

    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        ) {
            StatusBadge(tx)
            Text(
                text = tx.request.method,
                style = MaterialTheme.typography.titleSmall,
            )
            if (tx.response?.fromMock == true) {
                MockChip()
            }
            Spacer(Modifier.weight(1f))
            if (tx.response != null) {
                JwButton(text = "Mock this", onClick = onCreateMock)
            }
        }
        Text(
            text = tx.request.url,
            style = JwTypography.code,
            color = JwTheme.colors.textSecondary,
        )
        when {
            tx.failure != null -> Text(
                text = "Failed: ${tx.failure.message}",
                color = MaterialTheme.colorScheme.error,
            )

            tx.response != null -> Text(
                text = "${tx.response.statusCode} ${tx.response.statusDescription}  •  ${tx.response.durationMs}ms",
                style = MaterialTheme.typography.bodyMedium,
            )

            else -> EmptyHint("Pending…")
        }

        // Only surface the Query tab when the URL actually has query params — a permanently
        // disabled tab reads as broken. selectedTab only ever becomes Query while it is visible,
        // and it resets to Body/Headers per transaction, so it can't get stuck on a hidden tab.
        val tabs = remember(queryParams) {
            buildList {
                add(DetailTab.Body)
                add(DetailTab.Headers)
                if (queryParams.isNotEmpty()) add(DetailTab.Query)
            }
        }
        JwTabRow {
            tabs.forEach { tab ->
                JwTab(
                    text = tab.title,
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                )
            }
        }

        when (selectedTab) {
            DetailTab.Body -> BodyTab(tx)
            DetailTab.Headers -> HeadersTab(tx)
            DetailTab.Query -> QueryParamBlock(queryParams)
        }
    }
}

@Composable
private fun BodyTab(tx: HttpTransaction) {
    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        when {
            // The failure detail itself is shown above the tabs; here just note there is no body.
            tx.failure != null -> EmptyHint("Request failed — no response body")

            tx.response == null -> EmptyHint("Pending…")

            tx.response.body.isNullOrEmpty() -> EmptyHint("No response body")

            else -> BodyBlock(label = "body", body = tx.response.body, truncated = tx.response.bodyTruncated)
        }
        if (!tx.request.body.isNullOrEmpty()) {
            MinorLabel("Request body")
            BodyBlock(label = "body", body = tx.request.body, truncated = tx.request.bodyTruncated)
        }
    }
}

@Composable
private fun HeadersTab(tx: HttpTransaction) {
    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        SectionTitle("Request")
        if (tx.request.headers.isEmpty()) {
            EmptyHint("No request headers")
        } else {
            HeaderBlock(tx.request.headers)
        }

        SectionTitle("Response")
        when {
            tx.response == null -> EmptyHint("No response yet")
            tx.response.headers.isEmpty() -> EmptyHint("No response headers")
            else -> HeaderBlock(tx.response.headers)
        }
    }
}

@Composable
private fun QueryParamBlock(params: List<Pair<String, String>>) {
    if (params.isEmpty()) {
        EmptyHint("No query parameters")
        return
    }
    Column {
        params.forEach { (key, value) ->
            JwKeyValueRow(key = key, value = value, monospace = true)
        }
    }
}

@Composable
private fun MinorLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = JwTheme.colors.textSecondary,
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = JwTheme.colors.textDisabled,
    )
}

@Composable
private fun SectionTitle(text: String) {
    JwSectionHeader(
        title = text,
        modifier = Modifier.padding(top = JwSpacing.extraSmall),
    )
}

@Composable
private fun HeaderBlock(headers: Map<String, List<String>>) {
    if (headers.isEmpty()) return
    Column {
        headers.forEach { (key, values) ->
            JwKeyValueRow(key = key, value = values.joinToString(", "), monospace = true)
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
