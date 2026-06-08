package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kitakkun.jetwhale.host.sdk.JetWhaleDebugOperationContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawHostPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkEvent
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkMethod
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkMethodResult
import com.kitakkun.jetwhale.protocol.host.JetWhaleHostPluginProtocol
import com.kitakkun.jetwhale.protocol.host.kotlinxSerializationJetWhaleHostPluginProtocol
import kotlinx.coroutines.launch

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class NetworkHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleRawHostPlugin = NetworkHostPlugin()
}

private const val MAX_TRANSACTIONS = 500

private class NetworkHostPlugin : JetWhaleHostPlugin<NetworkEvent, NetworkMethod, NetworkMethodResult>() {

    override val protocol: JetWhaleHostPluginProtocol<NetworkEvent, NetworkMethod, NetworkMethodResult> =
        kotlinxSerializationJetWhaleHostPluginProtocol()

    private val transactions: SnapshotStateList<HttpTransaction> = mutableStateListOf()
    private val mockRules: SnapshotStateList<MockRule> = mutableStateListOf()
    private var mockingEnabled by mutableStateOf(true)

    override fun onEvent(event: NetworkEvent) {
        when (event) {
            is NetworkEvent.RequestSent -> {
                transactions.add(HttpTransaction(request = event.request))
                while (transactions.size > MAX_TRANSACTIONS) transactions.removeAt(0)
            }

            is NetworkEvent.ResponseReceived -> updateTransaction(event.response.txId) {
                it.copy(response = event.response)
            }

            is NetworkEvent.RequestFailed -> updateTransaction(event.failure.txId) {
                it.copy(failure = event.failure)
            }
        }
    }

    private inline fun updateTransaction(txId: String, transform: (HttpTransaction) -> HttpTransaction) {
        val index = transactions.indexOfFirst { it.request.txId == txId }
        if (index >= 0) transactions[index] = transform(transactions[index])
    }

    @Composable
    override fun Content(context: JetWhaleDebugOperationContext<NetworkMethod, NetworkMethodResult>) {
        NetworkInspectorScreen(
            transactions = transactions,
            mockRules = mockRules,
            mockingEnabled = mockingEnabled,
            onClearTransactions = { transactions.clear() },
            onToggleMocking = { enabled ->
                mockingEnabled = enabled
                context.coroutineScope.launch {
                    context.dispatch<NetworkMethodResult>(NetworkMethod.SetMockingEnabled(enabled))
                }
            },
            onMockRulesChanged = { rules ->
                mockRules.apply {
                    clear()
                    addAll(rules)
                }
                context.coroutineScope.launch {
                    context.dispatch<NetworkMethodResult>(NetworkMethod.SetMockRules(rules))
                }
            },
        )
    }
}
