package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.host.sdk.LocalIsScreenshotCapture
import com.kitakkun.jetwhale.plugins.network.protocol.GetMockConfig
import com.kitakkun.jetwhale.plugins.network.protocol.GetRedactionConfig
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionRule
import com.kitakkun.jetwhale.plugins.network.protocol.RequestFailed
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import com.kitakkun.jetwhale.plugins.network.protocol.SetMockRules
import com.kitakkun.jetwhale.plugins.network.protocol.SetMockingEnabled
import com.kitakkun.jetwhale.plugins.network.protocol.redact
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import com.kitakkun.jetwhale.protocol.messaging.request
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.util.UUID

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class NetworkHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = NetworkHostPlugin()
}

private const val MAX_TRANSACTIONS = 500

@OptIn(ExperimentalJetWhaleApi::class)
private class NetworkHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin {

    private val transactions: SnapshotStateList<HttpTransaction> = mutableStateListOf()
    private val mockRules: SnapshotStateList<MockRule> = mutableStateListOf()
    private var mockingEnabled by mutableStateOf(true)

    // MCP_ONLY redaction rules configured on the agent: applied to MCP tool results only, so the
    // host UI keeps showing the raw values. Empty when the agent predates GetRedactionConfig.
    private var mcpRedactionRules: List<RedactionRule> = emptyList()

    override fun JetWhaleMessageHandlers.configure() {
        onEvent { event: RequestSent ->
            transactions.add(HttpTransaction(request = event.request))
            while (transactions.size > MAX_TRANSACTIONS) transactions.removeAt(0)
        }
        onEvent { event: ResponseReceived ->
            updateTransaction(event.response.txId) { it.copy(response = event.response) }
        }
        onEvent { event: RequestFailed ->
            updateTransaction(event.failure.txId) { it.copy(failure = event.failure) }
        }
    }

    // The agent is the source of truth for the mock config (it survives host restarts): fetch and
    // adopt it before any traffic handler runs.
    override suspend fun onPrepare() {
        val config = messenger.request(GetMockConfig)
        mockingEnabled = config.enabled
        mockRules.apply {
            clear()
            addAll(config.rules)
        }
        mcpRedactionRules = try {
            messenger.request(GetRedactionConfig).mcpOnlyRules
        } catch (_: JetWhaleMessagingException) {
            // An agent built before GetRedactionConfig existed cannot answer; it has no
            // MCP_ONLY rules to enforce either.
            emptyList()
        }
    }

    private fun HttpTransaction.redactedForMcp(): HttpTransaction {
        if (mcpRedactionRules.isEmpty()) return this
        return copy(
            request = mcpRedactionRules.redact(request),
            response = response?.let { mcpRedactionRules.redact(it) },
        )
    }

    private inline fun updateTransaction(txId: String, transform: (HttpTransaction) -> HttpTransaction) {
        val index = transactions.indexOfFirst { it.request.txId == txId }
        if (index >= 0) transactions[index] = transform(transactions[index])
    }

    // Pushes the new rule set to the agent first and commits it locally only on success, so the
    // host view never drifts ahead of the agent's actual mocking behaviour.
    private suspend fun syncMockRules(newRules: List<MockRule>): JetWhaleMessagingException? {
        try {
            messenger.request(SetMockRules(newRules))
        } catch (e: JetWhaleMessagingException) {
            return e
        }
        mockRules.apply {
            clear()
            addAll(newRules)
        }
        return null
    }

    private suspend fun syncMockingEnabled(enabled: Boolean): JetWhaleMessagingException? {
        try {
            messenger.request(SetMockingEnabled(enabled))
        } catch (e: JetWhaleMessagingException) {
            return e
        }
        mockingEnabled = enabled
        return null
    }

    @Composable
    override fun Content() {
        // MCP screenshot captures are AI-agent-facing like tool results, so MCP_ONLY rules
        // apply to them too; the interactive window keeps showing the raw values.
        val redactForCapture = LocalIsScreenshotCapture.current && mcpRedactionRules.isNotEmpty()
        NetworkInspectorScreen(
            transactions = if (redactForCapture) transactions.map { it.redactedForMcp() } else transactions,
            mockRules = mockRules,
            mockingEnabled = mockingEnabled,
            onClearTransactions = { transactions.clear() },
            onToggleMocking = { enabled ->
                pluginScope.launch { syncMockingEnabled(enabled) }
            },
            onMockRulesChanged = { rules ->
                pluginScope.launch { syncMockRules(rules) }
            },
        )
    }

    // -------------------------------------------------------------------------
    // JetWhaleMcpCapablePlugin
    // -------------------------------------------------------------------------

    override fun mcpTools(): List<JetWhaleMcpToolDescriptor> = listOf(
        JetWhaleMcpToolDescriptor(
            name = "$TOOL_PREFIX.listTransactions",
            description = "Lists captured HTTP transactions (oldest first) as summaries: txId, method, url, timestamp, status, duration, mock/failure flags. Returns {\"transactions\": [...]} plus \"nextCursor\" when a cursor page was truncated. Use getTransaction for headers and bodies.",
            parameters = mapOf(
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
            ),
        ),
        JetWhaleMcpToolDescriptor(
            name = "$TOOL_PREFIX.getTransaction",
            description = "Returns the full detail of one captured HTTP transaction (request/response headers and bodies, or the failure).",
            parameters = mapOf(
                "txId" to JetWhaleMcpParameterDescriptor(
                    type = "string",
                    description = "The transaction id from listTransactions.",
                ),
            ),
        ),
        JetWhaleMcpToolDescriptor(
            name = "$TOOL_PREFIX.clearTransactions",
            description = "Clears the captured HTTP transaction list.",
        ),
        JetWhaleMcpToolDescriptor(
            name = "$TOOL_PREFIX.getMockConfig",
            description = "Returns the current mock configuration: the global enabled flag and all mock rules.",
        ),
        JetWhaleMcpToolDescriptor(
            name = "$TOOL_PREFIX.setMockingEnabled",
            description = "Enables or disables HTTP response mocking globally on the debuggee.",
            parameters = mapOf(
                "enabled" to JetWhaleMcpParameterDescriptor(
                    type = "boolean",
                    description = "true to enable mocking, false to disable.",
                ),
            ),
        ),
        JetWhaleMcpToolDescriptor(
            name = "$TOOL_PREFIX.addMockRule",
            description = "Adds a mock rule: requests matching the URL pattern (and optional method) receive the canned response instead of hitting the network. Returns the created rule.",
            parameters = mapOf(
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
            ),
        ),
        JetWhaleMcpToolDescriptor(
            name = "$TOOL_PREFIX.removeMockRule",
            description = "Removes the mock rule with the given id.",
            parameters = mapOf(
                "id" to JetWhaleMcpParameterDescriptor(
                    type = "string",
                    description = "The rule id from getMockConfig or addMockRule.",
                ),
            ),
        ),
    )

    override suspend fun handleMcpTool(toolName: String, arguments: Map<String, String>): String? = when (toolName) {
        "$TOOL_PREFIX.listTransactions" -> {
            val urlContains = arguments["urlContains"]
            val method = arguments["method"]
            val limit = arguments["limit"]?.toIntOrNull()
            val sinceTimestampMs = arguments["sinceTimestampMs"]?.toLongOrNull()
            val untilTimestampMs = arguments["untilTimestampMs"]?.toLongOrNull()
            val afterTxId = arguments["afterTxId"]

            // The cursor is resolved against the unfiltered capture list so it stays valid when
            // the caller changes filters between pages.
            val all = transactions.toList()
            val afterIndex = if (afterTxId != null) {
                val index = all.indexOfFirst { it.txId == afterTxId }
                if (index < 0) {
                    return errorJson(
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
            buildJsonObject {
                put("transactions", JsonArray(page.map { it.redactedForMcp().toSummaryJson() }))
                nextCursor?.let { put("nextCursor", it) }
            }.toString()
        }

        "$TOOL_PREFIX.getTransaction" -> {
            val txId = arguments["txId"] ?: return errorJson("missing required argument: txId")
            val transaction = transactions.toList().firstOrNull { it.txId == txId }
                ?: return errorJson("no transaction with txId: $txId")
            transaction.redactedForMcp().toDetailJson().toString()
        }

        "$TOOL_PREFIX.clearTransactions" -> {
            val cleared = transactions.size
            transactions.clear()
            buildJsonObject { put("clearedCount", cleared) }.toString()
        }

        "$TOOL_PREFIX.getMockConfig" -> {
            buildJsonObject {
                put("enabled", mockingEnabled)
                put("rules", Json.encodeToJsonElement(mockRules.toList()))
            }.toString()
        }

        "$TOOL_PREFIX.setMockingEnabled" -> {
            val enabled = arguments["enabled"]?.toBooleanStrictOrNull()
                ?: return errorJson("missing or invalid required argument: enabled")
            when (val failure = syncMockingEnabled(enabled)) {
                null -> buildJsonObject { put("enabled", enabled) }.toString()
                else -> errorJson("failed to apply on the debuggee: ${failure.message}")
            }
        }

        "$TOOL_PREFIX.addMockRule" -> {
            val urlPattern = arguments["urlPattern"] ?: return errorJson("missing required argument: urlPattern")
            val matchType = arguments["matchType"]?.let { raw ->
                MockMatchType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                    ?: return errorJson("invalid matchType: $raw (expected CONTAINS, EXACT, or REGEX)")
            } ?: MockMatchType.CONTAINS
            val statusCode = arguments["statusCode"]?.let {
                it.toIntOrNull() ?: return errorJson("invalid statusCode: $it")
            } ?: 200
            val delayMs = arguments["delayMs"]?.let {
                it.toLongOrNull() ?: return errorJson("invalid delayMs: $it")
            } ?: 0L
            val rule = MockRule(
                id = UUID.randomUUID().toString(),
                name = arguments["name"] ?: "",
                enabled = true,
                matcher = MockMatcher(
                    method = arguments["method"],
                    urlPattern = urlPattern,
                    matchType = matchType,
                ),
                response = MockResponseSpec(
                    statusCode = statusCode,
                    body = arguments["body"] ?: "",
                    delayMs = delayMs,
                ),
            )
            when (val failure = syncMockRules(mockRules.toList() + rule)) {
                null -> Json.encodeToJsonElement(rule).toString()
                else -> errorJson("failed to apply on the debuggee: ${failure.message}")
            }
        }

        "$TOOL_PREFIX.removeMockRule" -> {
            val id = arguments["id"] ?: return errorJson("missing required argument: id")
            val remaining = mockRules.toList().filterNot { it.id == id }
            if (remaining.size == mockRules.size) return errorJson("no mock rule with id: $id")
            when (val failure = syncMockRules(remaining)) {
                null -> buildJsonObject { put("removedId", id) }.toString()
                else -> errorJson("failed to apply on the debuggee: ${failure.message}")
            }
        }

        else -> null
    }

    private fun errorJson(message: String): String = buildJsonObject { put("error", message) }.toString()

    companion object {
        private const val TOOL_PREFIX = "com.kitakkun.jetwhale.network"
    }
}
