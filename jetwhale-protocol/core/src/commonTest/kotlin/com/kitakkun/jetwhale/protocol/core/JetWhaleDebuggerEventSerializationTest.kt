package com.kitakkun.jetwhale.protocol.core

import com.kitakkun.jetwhale.protocol.JetWhaleSerializationTest
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for ensuring stable serialization of [JetWhaleDebuggerEvent].
 */
class JetWhaleDebuggerEventSerializationTest : JetWhaleSerializationTest() {

    @Test
    fun `plugin frame message round-trips and carries the host frame discriminator`() {
        val event = JetWhaleDebuggerEvent.PluginFrameMessage(
            frame = PluginFrame.Request(
                pluginId = "example-plugin",
                correlationId = "corr-1",
                messageType = "example/ping",
                payload = "{}",
            ),
        )

        val encoded = json.encodeToString(event)
        assertTrue(encoded.contains(""""type":"event/host/plugin_frame""""), "missing discriminator: $encoded")

        val decoded = json.decodeFromString<JetWhaleDebuggerEvent>(encoded)
        assertEquals(event, assertIs<JetWhaleDebuggerEvent.PluginFrameMessage>(decoded))
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
