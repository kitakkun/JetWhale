package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.GetMockConfig
import com.kitakkun.jetwhale.plugins.network.protocol.GetRedactionConfig
import com.kitakkun.jetwhale.plugins.network.protocol.MockConfig
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionConfig
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionRule
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionScope
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionStrategy
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionTarget
import com.kitakkun.jetwhale.plugins.network.protocol.RequestSent
import com.kitakkun.jetwhale.plugins.network.protocol.ResponseReceived
import com.kitakkun.jetwhale.protocol.messaging.JetWhalePluginPeer
import com.kitakkun.jetwhale.protocol.messaging.reply
import com.kitakkun.jetwhale.protocol.messaging.trySend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

@OptIn(ExperimentalJetWhaleApi::class, InternalJetWhaleHostApi::class, ExperimentalCoroutinesApi::class)
class NetworkHostPluginTest {
    @Test
    fun `an mcp-only query rule hides a redirect's Location query value from getTransaction`() = runTest {
        lateinit var hostPeer: JetWhalePluginPeer
        val agentPeer = JetWhalePluginPeer(PLUGIN_ID, backgroundScope, sendFrame = { hostPeer.onFrame(it) })
        hostPeer = JetWhalePluginPeer(PLUGIN_ID, backgroundScope, sendFrame = agentPeer::onFrame)
        agentPeer.configure {
            onRequest { _: GetMockConfig -> reply(MockConfig(enabled = true, rules = emptyList())) }
            onRequest { _: GetRedactionConfig ->
                reply(RedactionConfig(listOf(RedactionRule(RedactionTarget.URL_QUERY_PARAM, "token", RedactionScope.MCP_ONLY, RedactionStrategy.PLACEHOLDER))))
            }
        }
        val plugin = NetworkHostPluginFactory().createPlugin() as JetWhaleMessagingHostPlugin
        plugin.bindMessenger(hostPeer.messenger)
        hostPeer.configure { plugin.registerHandlers(this) }
        plugin.dispatchPrepare()

        agentPeer.messenger.trySend(RequestSent(CapturedHttpRequest(txId = "tx-1", method = "GET", url = "https://api.example.com/login", timestampMs = 0L)))
        agentPeer.messenger.trySend(
            ResponseReceived(
                CapturedHttpResponse(
                    txId = "tx-1",
                    statusCode = 302,
                    headers = mapOf("Location" to listOf("https://api.example.com/callback?token=secret-value&page=2")),
                    durationMs = 10L,
                ),
            ),
        )
        runCurrent()

        val getTransaction = (plugin as JetWhaleMcpCapablePlugin).mcpCommands.single { it.name == "$TOOL_PREFIX.getTransaction" }
        val detail = getTransaction.execute(JetWhaleMcpArguments(buildJsonObject { put("txId", "tx-1") }))
        assertFalse("secret-value" in detail, detail)
        assertContains(detail, "callback?token=")
        assertContains(detail, "page=2")
    }

    private companion object {
        const val PLUGIN_ID = "com.kitakkun.jetwhale.network"
    }
}
