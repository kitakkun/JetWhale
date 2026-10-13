package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionRule
import com.kitakkun.jetwhale.plugins.network.protocol.redact
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListTransactionsCommand(
    private val transactions: () -> List<HttpTransaction>,
    private val mcpRedactionRules: () -> List<RedactionRule>?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listTransactions"
    override val description =
        "Lists captured HTTP transactions (oldest first) as summaries: txId, method, url, timestamp, status, duration, mock/failure flags. Returns {\"transactions\": [...]} plus \"nextCursor\" when a cursor page was truncated. Use getTransaction for headers and bodies."

    private val limit by intOrNull(
        "Maximum number of transactions to return. Without afterTxId it counts from the newest; with afterTxId it is the page size counted forward from the cursor. Returns all if omitted.",
    )
    private val afterTxId by stringOrNull(
        "Cursor: only include transactions captured after this txId (exclusive), oldest first. Pass the previous response's nextCursor to fetch the next page, or the last txId you have seen to fetch only new traffic.",
    )
    private val sinceTimestampMs by longOrNull(
        "Only include transactions whose request timestampMs is >= this epoch-millisecond value.",
    )
    private val untilTimestampMs by longOrNull(
        "Only include transactions whose request timestampMs is <= this epoch-millisecond value.",
    )
    private val urlContains by stringOrNull(
        "Only include transactions whose URL, as this tool returns it, contains this substring.",
    )
    private val method by stringOrNull(
        "Only include transactions with this HTTP method (case-insensitive).",
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val redactionRules = mcpRedactionRules() ?: return errorJson(MCP_REDACTION_RULES_UNREAD_ERROR)
        val limit = arguments[this.limit]
        val afterTxId = arguments[this.afterTxId]
        val filtered = matchingTransactions(arguments, redactionRules)

        val page = when {
            limit == null -> filtered
            afterTxId == null -> filtered.takeLast(limit)
            else -> filtered.take(limit)
        }
        val nextCursor = page.lastOrNull()?.txId?.takeIf { afterTxId != null && page.size < filtered.size }
        return buildJsonObject {
            put("transactions", JsonArray(page.map { redactionRules.redact(it).toSummaryJson() }))
            nextCursor?.let { put("nextCursor", it) }
        }.toString()
    }

    /** The captured transactions past the cursor that match every filter the call gave, oldest first. */
    private fun matchingTransactions(arguments: JetWhaleMcpArguments, redactionRules: List<RedactionRule>): List<HttpTransaction> {
        val afterTxId = arguments[this.afterTxId]
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

        val urlContains = arguments[this.urlContains]
        val method = arguments[this.method]
        val sinceTimestampMs = arguments[this.sinceTimestampMs]
        val untilTimestampMs = arguments[this.untilTimestampMs]
        return all.drop(afterIndex + 1)
            // Only the URL is matched, so the bodies are left out: redacting one parses and
            // re-serializes it.
            .filter { urlContains == null || redactionRules.redact(it.request.copy(body = null)).url.contains(urlContains) }
            .filter { method == null || it.request.method.equals(method, ignoreCase = true) }
            .filter { sinceTimestampMs == null || it.request.timestampMs >= sinceTimestampMs }
            .filter { untilTimestampMs == null || it.request.timestampMs <= untilTimestampMs }
    }
}
