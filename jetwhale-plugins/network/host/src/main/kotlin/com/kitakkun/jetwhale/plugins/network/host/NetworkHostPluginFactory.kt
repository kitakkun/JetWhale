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
import com.kitakkun.jetwhale.plugins.network.protocol.GetMockConfig
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.RequestFailed
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import com.kitakkun.jetwhale.plugins.network.protocol.SetMockRules
import com.kitakkun.jetwhale.plugins.network.protocol.SetMockingEnabled
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.request
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
    }

    private inline fun updateTransaction(txId: String, transform: (HttpTransaction) -> HttpTransaction) {
        val index = transactions.indexOfFirst { it.request.txId == txId }
        if (index >= 0) transactions[index] = transform(transactions[index])
    }

    @Composable
    override fun Content() {
        NetworkInspectorScreen(
            transactions = transactions,
            mockRules = mockRules,
            mockingEnabled = mockingEnabled,
            onClearTransactions = { transactions.clear() },
            onToggleMocking = { enabled ->
                mockingEnabled = enabled
                pluginScope.launch {
                    messenger.request(SetMockingEnabled(enabled))
                }
            },
            onMockRulesChanged = { rules ->
                mockRules.apply {
                    clear()
                    addAll(rules)
                }
                pluginScope.launch {
                    messenger.request(SetMockRules(rules))
                }
            },
        )
    }
}
