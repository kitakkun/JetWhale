package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import kotlinx.coroutines.launch
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.SplitPaneState
import java.awt.Cursor
import java.net.URLDecoder

/** Storage key for the Traffic tab's list/detail split position. */
private const val SPLIT_POSITION_KEY = "traffic.splitPosition"
private const val DEFAULT_SPLIT_POSITION = 0.42f
private val ListMinWidth = 240.dp
private val DetailMinWidth = 280.dp

@OptIn(ExperimentalSplitPaneApi::class)
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
    val splitPaneState = remember { SplitPaneState(DEFAULT_SPLIT_POSITION, moveEnabled = true) }
    // rememberPersistent hydrates from disk asynchronously, i.e. after SplitPaneState has already
    // been constructed, so the two are mirrored in both directions rather than seeded once. Both are
    // backed by mutableStateOf with structural equality, so echoing an unchanged value back does not
    // re-emit and the mirroring settles immediately.
    LaunchedEffect(splitPaneState) {
        launch {
            snapshotFlow { storedSplitPosition }
                .collect { splitPaneState.positionPercentage = it }
        }
        snapshotFlow { splitPaneState.positionPercentage }
            .collect { storedSplitPosition = it }
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
        HorizontalSplitPane(
            modifier = Modifier.fillMaxSize(),
            splitPaneState = splitPaneState,
        ) {
            first(minSize = ListMinWidth) {
                LazyColumn(
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
                ) {
                    items(visible, key = { it.txId }) { tx ->
                        ContextMenuArea(items = { transactionContextMenuItems(tx) }) {
                            TransactionRow(
                                tx = tx,
                                selected = tx.txId == selectedTxId,
                                onClick = { onSelectTx(tx.txId) },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
            second(minSize = DetailMinWidth) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                        // Detail pane values (URL, headers, bodies) are read-only reference data
                        // developers frequently copy, so make the whole pane text-selectable.
                        SelectionContainer {
                            TransactionDetail(tx = tx, onCreateMock = { onCreateMock(tx) })
                        }
                    }
                }
            }
            // Both parts must be declared: the DSL falls back to the default splitter — which draws
            // nothing at all — unless visiblePart *and* handle are set. The handle is a wider,
            // invisible hit area laid over the 1px divider so it stays comfortable to grab.
            splitter {
                visiblePart { VerticalDivider(Modifier.fillMaxHeight()) }
                handle {
                    Box(
                        Modifier
                            .markAsHandle()
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                            .width(8.dp)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

private fun transactionContextMenuItems(tx: HttpTransaction): List<ContextMenuItem> = buildList {
    add(ContextMenuItem("Copy as cURL") { copyToClipboard(buildCurlCommand(tx.request)) })
    add(ContextMenuItem("Copy URL") { copyToClipboard(tx.request.url) })
    tx.request.body?.let { body ->
        add(ContextMenuItem("Copy request body") { copyToClipboard(body) })
    }
    tx.response?.body?.let { body ->
        add(ContextMenuItem("Copy response body") { copyToClipboard(body) })
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
            // The list pane is narrow, so long URLs are read by scrolling the text sideways rather
            // than by selecting the row. maxLines = 1 plus softWrap = false keeps the URL on a
            // single line; weight(1f) fixes the viewport before horizontalScroll measures the text
            // against the unbounded width it hands down.
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
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

        tx.response.statusCode in 200..299 ->
            tx.response.statusCode.toString() to themeColor(Color(0xFF2E7D32), Color(0xFF66BB6A))

        tx.response.statusCode in 300..399 ->
            tx.response.statusCode.toString() to themeColor(Color(0xFF1565C0), Color(0xFF64B5F6))

        tx.response.statusCode >= 400 -> tx.response.statusCode.toString() to MaterialTheme.colorScheme.error

        else -> tx.response.statusCode.toString() to MaterialTheme.colorScheme.onSurface
    }
    Pill(label = label, color = color)
}

@Composable
private fun MockChip() {
    Pill(label = "MOCK", color = themeColor(Color(0xFF8E24AA), Color(0xFFCE93D8)))
}

@Composable
private fun Pill(label: String, color: Color) {
    // 14% of a dark hue on a dark surface nearly vanishes, so give the fill more presence in dark mode.
    val fillAlpha = if (isDarkTheme()) 0.22f else 0.14f
    Surface(
        color = color.copy(alpha = fillAlpha),
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
        when {
            tx.failure != null -> Text(
                text = "Failed: ${tx.failure.message}",
                color = MaterialTheme.colorScheme.error,
            )

            tx.response != null -> Text(
                text = "${tx.response.statusCode} ${tx.response.statusDescription}  •  ${tx.response.durationMs}ms",
                style = MaterialTheme.typography.bodyMedium,
            )

            else -> Text(
                text = "Pending…",
                color = MaterialTheme.colorScheme.outline,
            )
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
        TabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title) },
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            // The failure detail itself is shown above the tabs; here just note there is no body.
            tx.failure != null -> Text(
                text = "Request failed — no response body",
                color = MaterialTheme.colorScheme.outline,
            )

            tx.response == null -> Text(
                text = "Pending…",
                color = MaterialTheme.colorScheme.outline,
            )

            tx.response.body.isNullOrEmpty() -> Text(
                text = "No response body",
                color = MaterialTheme.colorScheme.outline,
            )

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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
private fun MinorLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
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
