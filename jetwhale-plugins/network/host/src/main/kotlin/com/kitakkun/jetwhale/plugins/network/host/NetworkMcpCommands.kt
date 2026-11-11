package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionRule
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.network"

/**
 * Each of the Network Inspector's MCP commands, built once, so the UI can ring the control or pane that
 * does what a running command does.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal class NetworkMcpCommands(
    transactions: () -> List<HttpTransaction>,
    mcpRedactionRules: () -> List<RedactionRule>?,
    removeAllTransactions: () -> Int,
    mockingEnabled: () -> Boolean,
    mockRules: () -> List<MockRule>,
    syncMockingEnabled: suspend (Boolean) -> JetWhaleMessagingException?,
    syncMockRules: suspend (List<MockRule>) -> JetWhaleMessagingException?,
) {
    val listTransactions = ListTransactionsCommand(transactions, mcpRedactionRules)
    val getTransaction = GetTransactionCommand(transactions, mcpRedactionRules)
    val clearTransactions = ClearTransactionsCommand(removeAllTransactions)
    val getMockConfig = GetMockConfigCommand(mockingEnabled, mockRules)
    val setMockingEnabled = SetMockingEnabledCommand(syncMockingEnabled)
    val addMockRule = AddMockRuleCommand(mockRules, syncMockRules)
    val removeMockRule = RemoveMockRuleCommand(mockRules, syncMockRules)
    val setMockRules = SetMockRulesCommand(syncMockRules)

    val all: List<JetWhaleMcpCommand> = listOf(listTransactions, getTransaction, clearTransactions, getMockConfig, setMockingEnabled, addMockRule, removeMockRule, setMockRules)
}

internal fun errorJson(message: String): String = buildJsonObject { put("error", message) }.toString()

internal const val MCP_REDACTION_RULES_UNREAD_ERROR =
    "transactions are withheld from MCP until the app's MCP-only redaction rules have been read; try again in a few seconds"

internal fun syncErrorJson(failure: JetWhaleMessagingException): String = errorJson("failed to apply on the debuggee: ${failure.message}")
