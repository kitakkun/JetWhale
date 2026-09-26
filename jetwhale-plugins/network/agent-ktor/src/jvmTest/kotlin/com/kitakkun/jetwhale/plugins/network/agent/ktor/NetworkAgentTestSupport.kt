package com.kitakkun.jetwhale.plugins.network.agent.ktor

import com.kitakkun.jetwhale.agent.sdk.messaging.JetWhaleOfflineCapableMessenger
import com.kitakkun.jetwhale.agent.sdk.messaging.OfflineSendPolicy
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import com.kitakkun.jetwhale.plugins.network.protocol.RequestFailed
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import com.kitakkun.jetwhale.plugins.network.protocol.SetNetworkConditions
import com.kitakkun.jetwhale.protocol.messaging.DefaultJetWhaleMessagingFormat
import com.kitakkun.jetwhale.protocol.messaging.JetWhalePluginPeer
import com.kitakkun.jetwhale.protocol.messaging.request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.StringFormat
import java.util.Collections
import kotlin.time.Duration

@OptIn(InternalJetWhaleApi::class)
internal fun agentWithEvents(): Pair<JetWhaleNetworkAgentPlugin, MutableList<Any>> {
    val agent = JetWhaleNetworkAgentPlugin()
    val recorder = RecordingMessenger(Collections.synchronizedList(mutableListOf()))
    agent.bindMessenger(recorder)
    return agent to recorder.events
}

// The agent's mock rules are set by the host over messaging in production; a unit test can't
// drive that internal path, so seed the state directly for the mock-serving case.
@Suppress("UNCHECKED_CAST")
internal fun JetWhaleNetworkAgentPlugin.seedMockRules(rules: List<MockRule>) {
    val field = JetWhaleNetworkAgentPlugin::class.java.getDeclaredField("mockRules").apply { isAccessible = true }
    (field.get(this) as MutableStateFlow<List<MockRule>>).value = rules
}

/** Delivers a SetNetworkConditions request to the agent's handlers through a real peer pair. */
@OptIn(InternalJetWhaleApi::class)
internal fun JetWhaleNetworkAgentPlugin.applyNetworkConditions(rules: List<NetworkConditionRule>) = runBlocking {
    val scope = CoroutineScope(SupervisorJob())
    lateinit var agentPeer: JetWhalePluginPeer
    val hostPeer = JetWhalePluginPeer(JetWhaleNetworkAgentPlugin.PLUGIN_ID, scope, sendFrame = { agentPeer.onFrame(it) })
    agentPeer = JetWhalePluginPeer(JetWhaleNetworkAgentPlugin.PLUGIN_ID, scope, sendFrame = hostPeer::onFrame)
    val agent = this@applyNetworkConditions
    agentPeer.configure { agent.registerHandlers(this) }
    hostPeer.messenger.request(SetNetworkConditions(rules))
    scope.cancel()
}

/** Records every event the agent sends, decoded back to its typed form. */
private class RecordingMessenger(val events: MutableList<Any>) : JetWhaleOfflineCapableMessenger {
    override val payloadFormat: StringFormat = DefaultJetWhaleMessagingFormat

    override fun sendRaw(messageType: String, payload: String, policy: OfflineSendPolicy): Boolean {
        events += when (messageType) {
            "network/request_sent" -> payloadFormat.decodeFromString(RequestSent.serializer(), payload)
            "network/response_received" -> payloadFormat.decodeFromString(ResponseReceived.serializer(), payload)
            "network/request_failed" -> payloadFormat.decodeFromString(RequestFailed.serializer(), payload)
            else -> error("Unexpected message type: $messageType")
        }
        return true
    }

    override suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String = error("The network agent plugin never requests the host in these tests.")
}
