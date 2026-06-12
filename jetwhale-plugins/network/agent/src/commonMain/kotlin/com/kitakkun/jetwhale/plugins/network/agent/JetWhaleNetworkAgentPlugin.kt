package com.kitakkun.jetwhale.plugins.network.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.Ack
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.GetMockConfig
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockConfig
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.RequestFailed
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import com.kitakkun.jetwhale.plugins.network.protocol.SetMockRules
import com.kitakkun.jetwhale.plugins.network.protocol.SetMockingEnabled
import com.kitakkun.jetwhale.plugins.network.protocol.findMatching
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingHandlers
import com.kitakkun.jetwhale.protocol.messaging.send
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
class JetWhaleNetworkAgentPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String get() = PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    // StateFlow gives thread-safe reads (adapter thread) / writes (messaging thread) without
    // a platform-specific lock.
    private val mockingEnabled = MutableStateFlow(true)
    private val mockRules = MutableStateFlow(emptyList<MockRule>())

    override fun JetWhaleMessagingHandlers.configure() {
        onRequest { request: SetMockRules ->
            mockRules.value = request.rules
            Ack
        }
        onRequest { request: SetMockingEnabled ->
            mockingEnabled.value = request.enabled
            Ack
        }
        onRequest { _: GetMockConfig ->
            MockConfig(enabled = mockingEnabled.value, rules = mockRules.value)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Adapter-facing capture API (transport-agnostic)
    // ---------------------------------------------------------------------------------------

    /** Generates a transaction id correlating a request with its response/failure. */
    @OptIn(ExperimentalUuidApi::class)
    fun newTransactionId(): String = Uuid.random().toString()

    fun recordRequest(request: CapturedHttpRequest) {
        messengerOrNull?.send(RequestSent(request))
    }

    fun recordResponse(response: CapturedHttpResponse) {
        messengerOrNull?.send(ResponseReceived(response))
    }

    fun recordFailure(failure: HttpRequestFailure) {
        messengerOrNull?.send(RequestFailed(failure))
    }

    /** Returns the mock response to serve for [method] [url], or null to perform the real call. */
    fun findMock(method: String, url: String): MockResponseSpec? = mockRules.value.findMatching(method = method, url = url, enabled = mockingEnabled.value)

    companion object {
        const val PLUGIN_ID: String = "com.kitakkun.jetwhale.network"
    }
}
