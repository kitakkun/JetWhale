package com.kitakkun.jetwhale.plugins.network.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkEvent
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkMethod
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkMethodResult
import com.kitakkun.jetwhale.plugins.network.protocol.findMatching
import com.kitakkun.jetwhale.protocol.agent.JetWhaleAgentPluginProtocol
import com.kitakkun.jetwhale.protocol.agent.kotlinxSerializationJetWhaleAgentPluginProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Transport-agnostic core of the Network Inspector agent plugin.
 *
 * It owns the mock configuration (pushed from the host) and exposes a small capture API
 * ([recordRequest]/[recordResponse]/[recordFailure]/[findMock]) that transport adapters call.
 * Today the only adapter is Ktor (`network:agent-ktor`); an OkHttp/Retrofit adapter could reuse
 * the exact same API without touching this class.
 */
class JetWhaleNetworkAgentPlugin : JetWhaleAgentPlugin<NetworkEvent, NetworkMethod, NetworkMethodResult>() {
    override val pluginId: String get() = PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"
    override val protocol: JetWhaleAgentPluginProtocol<NetworkEvent, NetworkMethod, NetworkMethodResult> =
        kotlinxSerializationJetWhaleAgentPluginProtocol()

    // StateFlow gives thread-safe reads (adapter thread) / writes (messaging thread) without
    // a platform-specific lock.
    private val mockingEnabled = MutableStateFlow(true)
    private val mockRules = MutableStateFlow(emptyList<com.kitakkun.jetwhale.plugins.network.protocol.MockRule>())

    override suspend fun onReceiveMethod(method: NetworkMethod): NetworkMethodResult = when (method) {
        is NetworkMethod.SetMockRules -> {
            mockRules.value = method.rules
            NetworkMethodResult.Ack
        }

        is NetworkMethod.SetMockingEnabled -> {
            mockingEnabled.value = method.enabled
            NetworkMethodResult.Ack
        }

        NetworkMethod.GetMockConfig -> NetworkMethodResult.MockConfig(
            enabled = mockingEnabled.value,
            rules = mockRules.value,
        )
    }

    // ---------------------------------------------------------------------------------------
    // Adapter-facing capture API (transport-agnostic)
    // ---------------------------------------------------------------------------------------

    /** Generates a transaction id correlating a request with its response/failure. */
    @OptIn(ExperimentalUuidApi::class)
    fun newTransactionId(): String = Uuid.random().toString()

    fun recordRequest(request: CapturedHttpRequest) {
        enqueueEvent(NetworkEvent.RequestSent(request))
    }

    fun recordResponse(response: CapturedHttpResponse) {
        enqueueEvent(NetworkEvent.ResponseReceived(response))
    }

    fun recordFailure(failure: HttpRequestFailure) {
        enqueueEvent(NetworkEvent.RequestFailed(failure))
    }

    /** Returns the mock response to serve for [method] [url], or null to perform the real call. */
    fun findMock(method: String, url: String): MockResponseSpec? = mockRules.value.findMatching(method = method, url = url, enabled = mockingEnabled.value)

    companion object {
        const val PLUGIN_ID: String = "com.kitakkun.jetwhale.network"
    }
}
