package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListTransactionsCommand(
    private val transactions: () -> List<HttpTransaction>,
    private val redactForMcp: (HttpTransaction) -> HttpTransaction,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listTransactions"
    override val description =
        "Lists captured HTTP transactions (oldest first) as summaries: txId, method, url, timestamp, status, duration, mock/failure flags. Returns {\"transactions\": [...]} plus \"nextCursor\" when a cursor page was truncated. Use getTransaction for headers and bodies."

    private val limitParam = optionalInt(
        "limit",
        "Maximum number of transactions to return. Without afterTxId it counts from the newest; with afterTxId it is the page size counted forward from the cursor. Returns all if omitted.",
    )
    private val afterTxIdParam = optionalString(
        "afterTxId",
        "Cursor: only include transactions captured after this txId (exclusive), oldest first. Pass the previous response's nextCursor to fetch the next page, or the last txId you have seen to fetch only new traffic.",
    )
    private val sinceTimestampMsParam = optionalLong(
        "sinceTimestampMs",
        "Only include transactions whose request timestampMs is >= this epoch-millisecond value.",
    )
    private val untilTimestampMsParam = optionalLong(
        "untilTimestampMs",
        "Only include transactions whose request timestampMs is <= this epoch-millisecond value.",
    )
    private val urlContainsParam = optionalString(
        "urlContains",
        "Only include transactions whose URL contains this substring.",
    )
    private val methodParam = optionalString(
        "method",
        "Only include transactions with this HTTP method (case-insensitive).",
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val urlContains = arguments[urlContainsParam]
        val method = arguments[methodParam]
        val limit = arguments[limitParam]
        val sinceTimestampMs = arguments[sinceTimestampMsParam]
        val untilTimestampMs = arguments[untilTimestampMsParam]
        val afterTxId = arguments[afterTxIdParam]

        // The cursor is resolved against the unfiltered capture list so it stays valid when
        // the caller changes filters between pages.
        val all = transactions()
        val afterIndex = if (afterTxId != null) {
            val index = all.indexOfFirst { it.txId == afterTxId }
            if (index < 0) {
                throw JetWhaleMcpArgumentException(
                    "unknown afterTxId: $afterTxId (the transaction may have been evicted or cleared; restart without a cursor)",
                )
            }
            index
        } else {
            -1
        }

        val filtered = all.drop(afterIndex + 1)
            .filter { urlContains == null || it.request.url.contains(urlContains) }
            .filter { method == null || it.request.method.equals(method, ignoreCase = true) }
            .filter { sinceTimestampMs == null || it.request.timestampMs >= sinceTimestampMs }
            .filter { untilTimestampMs == null || it.request.timestampMs <= untilTimestampMs }

        // Without a cursor, limit keeps its "latest N" meaning; with one, it pages forward.
        val page = when {
            limit == null -> filtered
            afterTxId == null -> filtered.takeLast(limit)
            else -> filtered.take(limit)
        }
        val nextCursor = page.lastOrNull()?.txId?.takeIf { afterTxId != null && page.size < filtered.size }
        return buildJsonObject {
            put("transactions", JsonArray(page.map { redactForMcp(it).toSummaryJson() }))
            nextCursor?.let { put("nextCursor", it) }
        }.toString()
    }
}
