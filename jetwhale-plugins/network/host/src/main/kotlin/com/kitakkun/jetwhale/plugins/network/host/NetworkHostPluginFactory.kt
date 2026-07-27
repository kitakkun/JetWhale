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
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.host.sdk.LocalIsScreenshotCapture
import com.kitakkun.jetwhale.plugins.network.protocol.GetMockConfig
import com.kitakkun.jetwhale.plugins.network.protocol.GetRedactionConfig
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

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        ListTransactionsCommand(transactions = { transactions.toList() }, redactForMcp = { it.redactedForMcp() }),
        GetTransactionCommand(transactions = { transactions.toList() }, redactForMcp = { it.redactedForMcp() }),
        ClearTransactionsCommand(
            clearTransactions = {
                val cleared = transactions.size
                transactions.clear()
                cleared
            },
        ),
        GetMockConfigCommand(mockingEnabled = { mockingEnabled }, mockRules = { mockRules.toList() }),
        SetMockingEnabledCommand(syncMockingEnabled = ::syncMockingEnabled),
        AddMockRuleCommand(mockRules = { mockRules.toList() }, syncMockRules = ::syncMockRules),
        RemoveMockRuleCommand(mockRules = { mockRules.toList() }, syncMockRules = ::syncMockRules),
        SetMockRulesCommand(syncMockRules = ::syncMockRules),
    )
}
