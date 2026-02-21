package com.kitakkun.jetwhale.protocol.negotiation

import com.kitakkun.jetwhale.protocol.JetWhaleSerializationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for ensuring stable serialization of [JetWhaleAgentNegotiationRequest].
 */
class JetWhaleAgentNegotiationRequestSerializationTest : JetWhaleSerializationTest() {

    @Test
    fun `protocol version negotiation request should be serialized stably`() {
        val request = JetWhaleAgentNegotiationRequest.ProtocolVersion(
            version = JetWhaleProtocolVersion.Current
        )

        val encoded = json.encodeToString(request)

        assertEquals(
            expected = """{"type":"negotiation/agent/protocol_version","version":${JetWhaleProtocolVersion.Current.version}}""",
            actual = encoded
        )
    }

    @Test
    fun `session negotiation request should be serialized stably`() {
        val request = JetWhaleAgentNegotiationRequest.Session(
            sessionId = null,
            sessionName = "SessionName"
        )

        val encoded = json.encodeToString(request)

        assertEquals(
            expected = """{"type":"negotiation/agent/session","sessionId":null,"sessionName":"SessionName"}""",
            actual = encoded
        )
    }

    @Test
    fun `capabilities negotiation request should be serialized stably`() {
        val request = JetWhaleAgentNegotiationRequest.Capabilities(
            capabilities = mapOf("feature1" to "enabled", "feature2" to "disabled")
        )

        val encoded = json.encodeToString(request)

        assertEquals(
            expected = """{"type":"negotiation/agent/capabilities","capabilities":{"feature1":"enabled","feature2":"disabled"}}""",
            actual = encoded
        )
    }

    @Test
    fun `capabilities negotiation request should be deserializable`() {
        val jsonString = """{"type":"negotiation/agent/capabilities","capabilities":{"key":"value"}}"""

        val decoded = json.decodeFromString<JetWhaleAgentNegotiationRequest>(jsonString)

        val capabilities = assertIs<JetWhaleAgentNegotiationRequest.Capabilities>(decoded)
        assertEquals(mapOf("key" to "value"), capabilities.capabilities)
    }

    @Test
    fun `available plugins negotiation request should be serialized stably`() {
        val request = JetWhaleAgentNegotiationRequest.AvailablePlugins(
            plugins = listOf(
                JetWhalePluginInfo(
                    pluginId = "example-plugin",
                    pluginVersion = "1.0.0",
                )
            )
        )

        val encoded = json.encodeToString(request)

        assertEquals(
            expected = """{"type":"negotiation/agent/available_plugins","plugins":[{"type":"model/plugin_info","pluginId":"example-plugin","pluginVersion":"1.0.0"}]}""",
            actual = encoded
        )
    }

    @Test
    fun `session negotiation request should be deserializable`() {
        val jsonString = """{"type":"negotiation/agent/session","sessionId":"session-1","sessionName":"My Session"}"""

        val decoded = json.decodeFromString<JetWhaleAgentNegotiationRequest>(jsonString)

        val session = assertIs<JetWhaleAgentNegotiationRequest.Session>(decoded)
        assertEquals("session-1", session.sessionId)
        assertEquals("My Session", session.sessionName)
    }
}
