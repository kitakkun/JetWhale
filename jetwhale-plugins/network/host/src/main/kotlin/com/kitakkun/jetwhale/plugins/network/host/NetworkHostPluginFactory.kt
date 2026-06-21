package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.host.sdk.LocalJetWhaleMessenger
import com.kitakkun.jetwhale.plugins.network.protocol.MockConfig
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.RequestFailed
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import com.kitakkun.jetwhale.plugins.network.protocol.SetMockRules
import com.kitakkun.jetwhale.plugins.network.protocol.SetMockingEnabled
import com.kitakkun.jetwhale.plugins.network.protocol.SyncMockConfig
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingHandlers
import com.kitakkun.jetwhale.protocol.messaging.SessionNegotiationScope
import com.kitakkun.jetwhale.protocol.messaging.receive
import com.kitakkun.jetwhale.protocol.messaging.request
import com.kitakkun.jetwhale.protocol.messaging.send
import kotlinx.coroutines.launch

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class NetworkHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = NetworkHostPlugin()
}

private const val MAX_TRANSACTIONS = 500

private class NetworkHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi {

    private val transactions: SnapshotStateList<HttpTransaction> = mutableStateListOf()
    private val mockRules: SnapshotStateList<MockRule> = mutableStateListOf()
    private var mockingEnabled by mutableStateOf(true)

    override fun JetWhaleMessagingHandlers.configure() {
        onEvent<RequestSent> { event ->
            transactions.add(HttpTransaction(request = event.request))
            while (transactions.size > MAX_TRANSACTIONS) transactions.removeAt(0)
        }
        onEvent<ResponseReceived> { event ->
            updateTransaction(event.response.txId) { it.copy(response = event.response) }
        }
        onEvent<RequestFailed> { event ->
            updateTransaction(event.failure.txId) { it.copy(failure = event.failure) }
        }
    }

    // The agent proposes the config it holds (it survives host restarts). Adopt it, show it, and
    // return the effective config for the agent to apply (a host-authoritative merge, if this plugin
    // ever needed one, would go here). The agent initiates, so we receive first.
    override suspend fun SessionNegotiationScope.negotiate() {
        val proposal = receive<SyncMockConfig>()
        mockingEnabled = proposal.config.enabled
        mockRules.apply {
            clear()
            addAll(proposal.config.rules)
        }
        send(MockConfig(enabled = mockingEnabled, rules = mockRules.toList()))
    }

    private inline fun updateTransaction(txId: String, transform: (HttpTransaction) -> HttpTransaction) {
        val index = transactions.indexOfFirst { it.request.txId == txId }
        if (index >= 0) transactions[index] = transform(transactions[index])
    }

    @Composable
    override fun Content() {
        val messenger = LocalJetWhaleMessenger.current
        NetworkInspectorScreen(
            transactions = transactions,
            mockRules = mockRules,
            mockingEnabled = mockingEnabled,
            onClearTransactions = { transactions.clear() },
            onToggleMocking = { enabled ->
                mockingEnabled = enabled
                messenger.coroutineScope.launch {
                    messenger.request(SetMockingEnabled(enabled))
                }
            },
            onMockRulesChanged = { rules ->
                mockRules.apply {
                    clear()
                    addAll(rules)
                }
                messenger.coroutineScope.launch {
                    messenger.request(SetMockRules(rules))
                }
            },
        )
    }
}
