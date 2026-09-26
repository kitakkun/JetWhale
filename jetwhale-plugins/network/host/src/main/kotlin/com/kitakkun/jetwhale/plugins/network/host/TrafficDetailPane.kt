package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.network.protocol.BodyEncoding
import com.kitakkun.jetwhale.plugins.network.protocol.mediaType
import java.net.URLDecoder

@Composable
internal fun TrafficDetailPane(
    transaction: HttpTransaction?,
    onCreateMock: (HttpTransaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(JwSpacing.large),
    ) {
        if (transaction == null) {
            JwEmptyState(title = "Select a request to see details")
        } else {
            // Detail pane values (URL, headers, bodies) are read-only reference data developers
            // frequently copy, so make the whole pane text-selectable.
            SelectionContainer {
                TransactionDetail(tx = transaction, onCreateMock = { onCreateMock(transaction) })
            }
        }
    }
}

private enum class DetailTab(val title: String) {
    Body("Body"),
    Headers("Headers"),
    Query("Query"),
}

@Composable
private fun TransactionDetail(tx: HttpTransaction, onCreateMock: () -> Unit, modifier: Modifier = Modifier) {
    val queryParams = remember(tx.request.url) { parseQueryParams(tx.request.url) }
    val hasResponseBody = !tx.response?.body.isNullOrEmpty()
    // Key on hasResponseBody too: the body often arrives after the row is first selected (same
    // txId), and the default should follow it to Body once it exists.
    var selectedTab by remember(tx.txId, hasResponseBody) {
        mutableStateOf(if (hasResponseBody) DetailTab.Body else DetailTab.Headers)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        ) {
            StatusBadge(tx)
            JwText(
                text = tx.request.method,
                style = JwTheme.textStyles.subtitle,
            )
            if (tx.response?.fromMock == true) {
                MockChip()
            }
            Spacer(Modifier.weight(1f))
            if (tx.response != null) {
                JwButton(text = "Mock this", onClick = onCreateMock)
            }
        }
        JwText(
            text = tx.request.url,
            style = JwTheme.textStyles.code,
            color = JwTheme.colors.textSecondary,
        )
        when {
            tx.failure != null -> JwText(
                text = "Failed: ${tx.failure.message}",
                color = JwTheme.colors.error,
            )

            tx.response != null -> JwText(
                text = "${tx.response.statusCode} ${tx.response.statusDescription}  •  ${tx.response.durationMs}ms",
                style = JwTheme.textStyles.body,
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
    // Bound to locals so the null checks below smart-cast: both bodies come from another module.
    val responseBody = tx.response?.body
    val requestBody = tx.request.body
    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        when {
            // The failure detail itself is shown above the tabs; here just note there is no body.
            tx.failure != null -> EmptyHint("Request failed — no response body")

            tx.response == null -> EmptyHint("Pending…")

            responseBody.isNullOrEmpty() -> EmptyHint("No response body")

            tx.response.bodyEncoding == BodyEncoding.BASE64 -> ImageBodyBlock(
                body = responseBody,
                mediaType = tx.response.headers.mediaType(),
                url = tx.request.url,
                truncated = tx.response.bodyTruncated,
            )

            else -> BodyBlock(label = "body", body = responseBody, truncated = tx.response.bodyTruncated)
        }
        if (!requestBody.isNullOrEmpty()) {
            MinorLabel("Request body")
            if (tx.request.bodyEncoding == BodyEncoding.BASE64) {
                ImageBodyBlock(
                    body = requestBody,
                    mediaType = tx.request.headers.mediaType(),
                    url = tx.request.url,
                    truncated = tx.request.bodyTruncated,
                )
            } else {
                BodyBlock(label = "body", body = requestBody, truncated = tx.request.bodyTruncated)
            }
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
    JwText(
        text = text,
        style = JwTheme.textStyles.label,
        color = JwTheme.colors.textSecondary,
    )
}

@Composable
private fun EmptyHint(text: String) {
    JwText(
        text = text,
        style = JwTheme.textStyles.bodySmall,
        color = JwTheme.colors.textSecondary,
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
    return query.split('&').filter(String::isNotBlank).map { part ->
        val index = part.indexOf('=')
        if (index < 0) {
            urlDecode(part) to ""
        } else {
            urlDecode(part.substring(0, index)) to urlDecode(part.substring(index + 1))
        }
    }
}

private fun urlDecode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
