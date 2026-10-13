package com.kitakkun.jetwhale.plugins.example.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.example.protocol.Pong
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequestException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

@OptIn(ExperimentalJetWhaleApi::class)
class ExampleHostPluginTest {
    @Test
    fun `a Ping the debuggee does not answer is a failed call`() {
        val result = sendPing(answer = { throw JetWhaleRequestException("timed out") })

        assertEquals(true, result.isError)
        assertEquals("the debuggee did not answer Ping: timed out", (result.content.single() as JetWhaleMcpContent.Text).text)
    }

    @Test
    fun `a Ping the debuggee answers reports the Pong`() {
        val result = sendPing(answer = { Json.encodeToString(Pong.serializer(), Pong) })

        assertEquals(false, result.isError)
        assertEquals("true", result.structuredContent?.get("pongReceived")?.toString())
    }

    @OptIn(InternalJetWhaleHostApi::class)
    private fun sendPing(answer: () -> String): JetWhaleMcpResult {
        val plugin = ExampleHostPluginFactory().createPlugin()
        (plugin as JetWhaleMessagingHostPlugin).bindMessenger(AnsweringMessenger(answer))
        val command = (plugin as JetWhaleMcpCapablePlugin).mcpCommands.single { it.name == "com.kitakkun.jetwhale.example.sendPing" }
        return runBlocking { command.run(JetWhaleMcpArguments(JsonObject(emptyMap()))) }
    }
}

private class AnsweringMessenger(private val answer: () -> String) : JetWhaleMessenger {
    override val payloadFormat: StringFormat = Json

    override fun sendRaw(messageType: String, payload: String): Boolean = true

    override suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String = answer()
}
