package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.agent.sdk.messaging.JetWhaleMessenger
import com.kitakkun.jetwhale.agent.sdk.messaging.OfflineSendPolicy
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import com.kitakkun.jetwhale.protocol.messaging.request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

@Serializable
@SerialName("prepare-test/get-state")
private data object GetState : JetWhaleRequest<State>

@Serializable
@SerialName("prepare-test/state")
private data class State(val value: String)

/** A messenger that answers every request with a canned reply and records what was asked. */
private class FakeMessenger(
    private val scope: CoroutineScope,
    private val cannedReply: String,
) : JetWhaleMessenger {
    override val payloadFormat: StringFormat = Json

    val requested: MutableList<String> = mutableListOf()

    override fun sendRaw(messageType: String, payload: String, policy: OfflineSendPolicy): Boolean = true

    override suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String {
        requested += messageType
        return cannedReply
    }
}

@OptIn(InternalJetWhaleApi::class)
class PluginPrepareTest {
    @Test
    fun `onPrepare exchanges initial state over the plugin's own messenger`() = runBlocking {
        val plugin = object : JetWhaleAgentPlugin() {
            override val pluginId: String = "prepare-test"
            override val pluginVersion: String = "1.0.0"
            var applied: String? = null

            override suspend fun onPrepare() {
                applied = messenger.request(GetState).value
            }
        }
        val fake = FakeMessenger(this, cannedReply = """{"value":"synced"}""")
        plugin.bindMessenger(fake)

        plugin.dispatchPrepare()

        assertEquals(listOf("prepare-test/get-state"), fake.requested)
        assertEquals("synced", plugin.applied)
    }
}
