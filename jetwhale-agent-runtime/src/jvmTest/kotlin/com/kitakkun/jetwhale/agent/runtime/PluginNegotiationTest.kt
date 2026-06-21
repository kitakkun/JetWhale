package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleNegotiationException
import com.kitakkun.jetwhale.protocol.messaging.RawNegotiationMessage
import com.kitakkun.jetwhale.protocol.messaging.SessionNegotiationScope
import com.kitakkun.jetwhale.protocol.messaging.receive
import com.kitakkun.jetwhale.protocol.messaging.send
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Serializable
@SerialName("negotiation-test/sync")
private data class Sync(val value: String)

@Serializable
@SerialName("negotiation-test/sync-reply")
private data class SyncReply(val echoed: String)

/** A negotiation scope that hands out canned incoming messages and records what was sent. */
private class FakeNegotiationScope(incoming: List<RawNegotiationMessage>) : SessionNegotiationScope {
    override val payloadFormat: StringFormat = Json
    private val inbox = ArrayDeque(incoming)
    val sent: MutableList<RawNegotiationMessage> = mutableListOf()

    override suspend fun sendRaw(messageType: String, payload: String) {
        sent += RawNegotiationMessage(messageType, payload)
    }

    override suspend fun receiveRaw(): RawNegotiationMessage = inbox.removeFirst()
}

@OptIn(InternalJetWhaleApi::class)
class PluginNegotiationTest {
    @Test
    fun `the negotiate script sends, then applies what it receives`() = runBlocking {
        val applied = mutableListOf<String>()
        val plugin = object : JetWhaleAgentPlugin() {
            override val pluginId: String get() = "negotiation-test"
            override val pluginVersion: String get() = "1.0.0"
            override suspend fun SessionNegotiationScope.negotiate() {
                send(Sync("hello"))
                applied += receive<SyncReply>().echoed
            }
        }

        val scope = FakeNegotiationScope(
            listOf(RawNegotiationMessage("negotiation-test/sync-reply", Json.encodeToString(SyncReply("ok")))),
        )
        plugin.runNegotiation(scope)

        assertEquals(1, scope.sent.size, "the agent should send its opening message")
        assertEquals("negotiation-test/sync", scope.sent.single().messageType)
        assertEquals(listOf("ok"), applied, "the received reply should be applied")
    }

    @Test
    fun `receive throws on an unexpected message type instead of misreading it`() = runBlocking {
        val plugin = object : JetWhaleAgentPlugin() {
            override val pluginId: String get() = "negotiation-test"
            override val pluginVersion: String get() = "1.0.0"
            override suspend fun SessionNegotiationScope.negotiate() {
                receive<SyncReply>() // but the peer sent the wrong type
            }
        }

        val scope = FakeNegotiationScope(listOf(RawNegotiationMessage("negotiation-test/unexpected", "{}")))
        val e = assertFailsWith<JetWhaleNegotiationException> {
            plugin.runNegotiation(scope)
        }
        assertTrue("negotiation-test/sync-reply" in (e.message ?: ""), "unexpected message: ${e.message}")
    }
}
