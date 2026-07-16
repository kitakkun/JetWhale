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
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import com.kitakkun.jetwhale.protocol.messaging.sendOrQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Transport-agnostic core of the Network Inspector agent plugin.
 *
 * It owns the mock configuration (pushed from the host) and exposes a small capture API
 * ([recordRequest]/[recordResponse]/[recordFailure]/[findMock]) that transport adapters call.
 * Adapters exist for Ktor (`network:agent-ktor`) and OkHttp (`network:agent-okhttp`); a
 * Retrofit adapter could reuse the exact same API without touching this class.
 */
class JetWhaleNetworkAgentPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String get() = PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    // Captured traffic must survive a disconnect: buffer it while the host is away and flush on
    // reconnect (oldest dropped past this bound). recordRequest/Response/Failure use sendOrQueue.
    override val offlineEventBufferCapacity: Int get() = OFFLINE_CAPTURE_BUFFER_CAPACITY

    // StateFlow gives thread-safe reads (adapter thread) / writes (messaging thread) without
    // a platform-specific lock.
    private val mockingEnabled = MutableStateFlow(true)
    private val mockRules = MutableStateFlow(emptyList<MockRule>())

    override fun JetWhaleMessageHandlers.configure() {
        // We are the config's source of truth (it survives host restarts); the host fetches it in onPrepare.
        onRequest { _: GetMockConfig ->
            reply(MockConfig(enabled = mockingEnabled.value, rules = mockRules.value))
        }
        onRequest { request: SetMockRules ->
            mockRules.value = request.rules
            reply(Ack)
        }
        onRequest { request: SetMockingEnabled ->
            mockingEnabled.value = request.enabled
            reply(Ack)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Adapter-facing capture API (transport-agnostic)
    // ---------------------------------------------------------------------------------------

    /** Generates a transaction id correlating a request with its response/failure. */
    @OptIn(ExperimentalUuidApi::class)
    fun newTransactionId(): String = Uuid.random().toString()

    fun recordRequest(request: CapturedHttpRequest) {
        messenger.sendOrQueue(RequestSent(request))
    }

    fun recordResponse(response: CapturedHttpResponse) {
        messenger.sendOrQueue(ResponseReceived(response))
    }

    fun recordFailure(failure: HttpRequestFailure) {
        messenger.sendOrQueue(RequestFailed(failure))
    }

    /** Returns the mock response to serve for [method] [url], or null to perform the real call. */
    fun findMock(method: String, url: String): MockResponseSpec? = mockRules.value.findMatching(method = method, url = url, enabled = mockingEnabled.value)

    companion object {
        const val PLUGIN_ID: String = "com.kitakkun.jetwhale.network"

        /** How many captured events to retain across a host disconnect before dropping the oldest. */
        private const val OFFLINE_CAPTURE_BUFFER_CAPACITY: Int = 256
    }
}
