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
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequestException
import com.kitakkun.jetwhale.protocol.messaging.reply
import com.kitakkun.jetwhale.protocol.messaging.trySend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

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

    @Test
    fun `transactions stay out of mcp until the mcp-only rules are read and are redacted once they are`() = runTest {
        var agentHasPluginActive = false
        lateinit var hostPeer: JetWhalePluginPeer
        val agentPeer = JetWhalePluginPeer(PLUGIN_ID, backgroundScope, sendFrame = { hostPeer.onFrame(it) })
        hostPeer = JetWhalePluginPeer(PLUGIN_ID, backgroundScope, sendFrame = agentPeer::onFrame)
        agentPeer.configure {
            onRequest { _: GetMockConfig ->
                check(agentHasPluginActive) { NOT_ACTIVE_IN_AGENT }
                reply(MockConfig(enabled = true, rules = emptyList()))
            }
            onRequest { _: GetRedactionConfig ->
                check(agentHasPluginActive) { NOT_ACTIVE_IN_AGENT }
                reply(RedactionConfig(listOf(RedactionRule(RedactionTarget.URL_QUERY_PARAM, "token", RedactionScope.MCP_ONLY, RedactionStrategy.PLACEHOLDER))))
            }
        }
        val plugin = NetworkHostPluginFactory().createPlugin() as JetWhaleMessagingHostPlugin
        plugin.bindPluginScope(backgroundScope)
        plugin.bindMessenger(hostPeer.messenger)
        hostPeer.configure { plugin.registerHandlers(this) }
        assertFailsWith<JetWhaleRequestException> { plugin.dispatchPrepare() }

        agentPeer.messenger.trySend(RequestSent(CapturedHttpRequest(txId = "tx-1", method = "GET", url = "https://api.example.com/login?token=secret-value&page=2", timestampMs = 0L)))
        runCurrent()

        val mcpCommands = (plugin as JetWhaleMcpCapablePlugin).mcpCommands
        val listTransactions = mcpCommands.single { it.name == "$TOOL_PREFIX.listTransactions" }
        val getTransaction = mcpCommands.single { it.name == "$TOOL_PREFIX.getTransaction" }
        val txArguments = JetWhaleMcpArguments(buildJsonObject { put("txId", "tx-1") })
        assertEquals(errorJson(MCP_REDACTION_RULES_UNREAD_ERROR), listTransactions.execute(JetWhaleMcpArguments(buildJsonObject {})))
        assertEquals(errorJson(MCP_REDACTION_RULES_UNREAD_ERROR), getTransaction.execute(txArguments))

        agentHasPluginActive = true
        advanceTimeBy(5.seconds)
        runCurrent()

        val detail = getTransaction.execute(txArguments)
        assertFalse("secret-value" in detail, detail)
        assertContains(detail, "login?token=")
        assertContains(detail, "page=2")
    }

    private companion object {
        const val PLUGIN_ID = "com.kitakkun.jetwhale.network"
        const val NOT_ACTIVE_IN_AGENT = "Plugin 'com.kitakkun.jetwhale.network' is not active in the agent."
    }
}
