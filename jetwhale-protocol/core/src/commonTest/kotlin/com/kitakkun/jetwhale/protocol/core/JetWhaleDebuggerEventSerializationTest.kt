package com.kitakkun.jetwhale.protocol.core

import com.kitakkun.jetwhale.protocol.JetWhaleSerializationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for ensuring stable serialization of [JetWhaleDebuggerEvent].
 */
class JetWhaleDebuggerEventSerializationTest : JetWhaleSerializationTest() {

    @Test
    fun `method request event should be serialized stably`() {
        val event = JetWhaleDebuggerEvent.MethodRequest(
            pluginId = "example-plugin",
            requestId = "req-1",
            payload = "doSomething",
        )

        val encoded = json.encodeToString(event)

        assertEquals(
            expected = """{"type":"event/host/plugin_method_request","pluginId":"example-plugin","requestId":"req-1","payload":"doSomething"}""",
            actual = encoded,
        )
    }

    @Test
    fun `method request event should be deserializable`() {
        val jsonString =
            """{"type":"event/host/plugin_method_request","pluginId":"example-plugin","requestId":"req-2","payload":"doSomethingElse"}"""

        val decoded = json.decodeFromString<JetWhaleDebuggerEvent>(jsonString)

        val request = assertIs<JetWhaleDebuggerEvent.MethodRequest>(decoded)
        assertEquals("example-plugin", request.pluginId)
        assertEquals("req-2", request.requestId)
        assertEquals("doSomethingElse", request.payload)
    }

    @Test
    fun `plugin activated event should be serialized stably`() {
        val event = JetWhaleDebuggerEvent.PluginActivated(
            pluginId = "example-plugin",
        )

        val encoded = json.encodeToString(event)

        assertEquals(
            expected = """{"type":"event/host/plugin_activated","pluginId":"example-plugin"}""",
            actual = encoded,
        )
    }

    @Test
    fun `plugin activated event should be deserializable`() {
        val jsonString =
            """{"type":"event/host/plugin_activated","pluginId":"example-plugin"}"""

        val decoded = json.decodeFromString<JetWhaleDebuggerEvent>(jsonString)

        val activated = assertIs<JetWhaleDebuggerEvent.PluginActivated>(decoded)
        assertEquals("example-plugin", activated.pluginId)
    }

    @Test
    fun `plugin deactivated event should be serialized stably`() {
        val event = JetWhaleDebuggerEvent.PluginDeactivated(
            pluginId = "example-plugin",
        )

        val encoded = json.encodeToString(event)

        assertEquals(
            expected = """{"type":"event/host/plugin_deactivated","pluginId":"example-plugin"}""",
            actual = encoded,
        )
    }

    @Test
    fun `plugin deactivated event should be deserializable`() {
        val jsonString =
            """{"type":"event/host/plugin_deactivated","pluginId":"example-plugin"}"""

        val decoded = json.decodeFromString<JetWhaleDebuggerEvent>(jsonString)

        val deactivated = assertIs<JetWhaleDebuggerEvent.PluginDeactivated>(decoded)
        assertEquals("example-plugin", deactivated.pluginId)
    }
}
