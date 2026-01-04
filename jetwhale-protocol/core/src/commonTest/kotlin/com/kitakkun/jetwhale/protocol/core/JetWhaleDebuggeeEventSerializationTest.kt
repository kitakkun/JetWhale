package com.kitakkun.jetwhale.protocol.core

import com.kitakkun.jetwhale.protocol.JetWhaleSerializationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for ensuring stable serialization of [JetWhaleDebuggeeEvent].
 */
class JetWhaleDebuggeeEventSerializationTest : JetWhaleSerializationTest() {

    @Test
    fun `plugin message event should be serialized stably`() {
        val event = JetWhaleDebuggeeEvent.PluginMessage(
            pluginId = "example-plugin",
            payload = "hello"
        )

        val encoded = json.encodeToString(event)

        assertEquals(
            expected = """{"type":"event/agent/plugin_message","pluginId":"example-plugin","payload":"hello"}""",
            actual = encoded
        )
    }

    @Test
    fun `method result success event should be serialized stably`() {
        val event = JetWhaleDebuggeeEvent.MethodResultResponse.Success(
            requestId = "req-1",
            payload = "result"
        )

        val encoded = json.encodeToString(event)

        assertEquals(
            expected = """{"type":"event/agent/method_result_response/success","requestId":"req-1","payload":"result"}""",
            actual = encoded
        )
    }

    @Test
    fun `method result failed event should be serialized stably`() {
        val event = JetWhaleDebuggeeEvent.MethodResultResponse.Failed(
            requestId = "req-2",
            errorMessage = "something went wrong"
        )

        val encoded = json.encodeToString(event)

        assertEquals(
            expected = """{"type":"event/agent/method_result_response/failed","requestId":"req-2","errorMessage":"something went wrong"}""",
            actual = encoded
        )
    }

    @Test
    fun `method result response should be deserializable`() {
        val jsonString = """{"type":"event/agent/method_result_response/success","requestId":"req-3","payload":"ok"}"""

        val decoded = json.decodeFromString<JetWhaleDebuggeeEvent>(jsonString)

        val success = assertIs<JetWhaleDebuggeeEvent.MethodResultResponse.Success>(decoded)
        assertEquals("req-3", success.requestId)
        assertEquals("ok", success.payload)
    }
}
