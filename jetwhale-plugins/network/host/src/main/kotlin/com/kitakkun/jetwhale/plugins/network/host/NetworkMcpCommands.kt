package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.util.UUID

internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.network"

internal fun errorJson(message: String): String = buildJsonObject { put("error", message) }.toString()

private fun syncErrorJson(failure: JetWhaleMessagingException): String = errorJson("failed to apply on the debuggee: ${failure.message}")

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListTransactionsCommand(
    private val transactions: () -> List<HttpTransaction>,
    private val redactForMcp: (HttpTransaction) -> HttpTransaction,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listTransactions"
    override val description =
        "Lists captured HTTP transactions (oldest first) as summaries: txId, method, url, timestamp, status, duration, mock/failure flags. Returns {\"transactions\": [...]} plus \"nextCursor\" when a cursor page was truncated. Use getTransaction for headers and bodies."
    override val parameters = mapOf(
        "limit" to JetWhaleMcpParameterDescriptor(
            type = "integer",
            description = "Maximum number of transactions to return. Without afterTxId it counts from the newest; with afterTxId it is the page size counted forward from the cursor. Returns all if omitted.",
            required = false,
        ),
        "afterTxId" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "Cursor: only include transactions captured after this txId (exclusive), oldest first. Pass the previous response's nextCursor to fetch the next page, or the last txId you have seen to fetch only new traffic.",
            required = false,
        ),
        "sinceTimestampMs" to JetWhaleMcpParameterDescriptor(
            type = "integer",
            description = "Only include transactions whose request timestampMs is >= this epoch-millisecond value.",
            required = false,
        ),
        "untilTimestampMs" to JetWhaleMcpParameterDescriptor(
            type = "integer",
            description = "Only include transactions whose request timestampMs is <= this epoch-millisecond value.",
            required = false,
        ),
        "urlContains" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "Only include transactions whose URL contains this substring.",
            required = false,
        ),
        "method" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "Only include transactions with this HTTP method (case-insensitive).",
            required = false,
        ),
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val urlContains = arguments.optionalString("urlContains")
        val method = arguments.optionalString("method")
        val limit = arguments.optionalInt("limit")
        val sinceTimestampMs = arguments.optionalLong("sinceTimestampMs")
        val untilTimestampMs = arguments.optionalLong("untilTimestampMs")
        val afterTxId = arguments.optionalString("afterTxId")

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

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetTransactionCommand(
    private val transactions: () -> List<HttpTransaction>,
    private val redactForMcp: (HttpTransaction) -> HttpTransaction,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getTransaction"
    override val description = "Returns the full detail of one captured HTTP transaction (request/response headers and bodies, or the failure)."
    override val parameters = mapOf(
        "txId" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "The transaction id from listTransactions.",
        ),
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val txId = arguments.requireString("txId")
        val transaction = transactions().firstOrNull { it.txId == txId }
            ?: throw JetWhaleMcpArgumentException("no transaction with txId: $txId")
        return redactForMcp(transaction).toDetailJson().toString()
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class ClearTransactionsCommand(
    private val clearTransactions: () -> Int,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.clearTransactions"
    override val description = "Clears the captured HTTP transaction list."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = buildJsonObject { put("clearedCount", clearTransactions()) }.toString()
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetMockConfigCommand(
    private val mockingEnabled: () -> Boolean,
    private val mockRules: () -> List<MockRule>,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getMockConfig"
    override val description = "Returns the current mock configuration: the global enabled flag and all mock rules."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = buildJsonObject {
        put("enabled", mockingEnabled())
        put("rules", Json.encodeToJsonElement(mockRules()))
    }.toString()
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetMockingEnabledCommand(
    private val syncMockingEnabled: suspend (Boolean) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.setMockingEnabled"
    override val description = "Enables or disables HTTP response mocking globally on the debuggee."
    override val parameters = mapOf(
        "enabled" to JetWhaleMcpParameterDescriptor(
            type = "boolean",
            description = "true to enable mocking, false to disable.",
        ),
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val enabled = arguments.requireBoolean("enabled")
        return when (val failure = syncMockingEnabled(enabled)) {
            null -> buildJsonObject { put("enabled", enabled) }.toString()
            else -> syncErrorJson(failure)
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class AddMockRuleCommand(
    private val mockRules: () -> List<MockRule>,
    private val syncMockRules: suspend (List<MockRule>) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.addMockRule"
    override val description =
        "Adds a mock rule: requests matching the URL pattern (and optional method) receive the canned response instead of hitting the network. Returns the created rule."
    override val parameters = mapOf(
        "urlPattern" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "URL pattern to match, interpreted per matchType.",
        ),
        "matchType" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "How urlPattern is compared: CONTAINS, EXACT, or REGEX. Defaults to CONTAINS.",
            required = false,
        ),
        "method" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "HTTP method to match (case-insensitive). Matches any method if omitted.",
            required = false,
        ),
        "name" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "Human-readable rule name shown in the UI.",
            required = false,
        ),
        "statusCode" to JetWhaleMcpParameterDescriptor(
            type = "integer",
            description = "Status code of the mocked response. Defaults to 200.",
            required = false,
        ),
        "body" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "Body of the mocked response. Defaults to empty.",
            required = false,
        ),
        "delayMs" to JetWhaleMcpParameterDescriptor(
            type = "integer",
            description = "Artificial delay before the mocked response is delivered, in milliseconds. Defaults to 0.",
            required = false,
        ),
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val rule = MockRule(
            id = UUID.randomUUID().toString(),
            name = arguments.optionalString("name") ?: "",
            enabled = true,
            matcher = MockMatcher(
                method = arguments.optionalString("method"),
                urlPattern = arguments.requireString("urlPattern"),
                matchType = arguments.optionalEnum("matchType", MockMatchType.entries) ?: MockMatchType.CONTAINS,
            ),
            response = MockResponseSpec(
                statusCode = arguments.optionalInt("statusCode") ?: 200,
                body = arguments.optionalString("body") ?: "",
                delayMs = arguments.optionalLong("delayMs") ?: 0L,
            ),
        )
        return when (val failure = syncMockRules(mockRules() + rule)) {
            null -> Json.encodeToJsonElement(rule).toString()
            else -> syncErrorJson(failure)
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class RemoveMockRuleCommand(
    private val mockRules: () -> List<MockRule>,
    private val syncMockRules: suspend (List<MockRule>) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.removeMockRule"
    override val description = "Removes the mock rule with the given id."
    override val parameters = mapOf(
        "id" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "The rule id from getMockConfig or addMockRule.",
        ),
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val id = arguments.requireString("id")
        val current = mockRules()
        val remaining = current.filterNot { it.id == id }
        if (remaining.size == current.size) throw JetWhaleMcpArgumentException("no mock rule with id: $id")
        return when (val failure = syncMockRules(remaining)) {
            null -> buildJsonObject { put("removedId", id) }.toString()
            else -> syncErrorJson(failure)
        }
    }
}
