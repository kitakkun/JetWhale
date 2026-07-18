package com.kitakkun.jetwhale.protocol.negotiation

import com.kitakkun.jetwhale.protocol.JetWhaleSerializationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for ensuring stable serialization of [JetWhaleHostNegotiationResponse].
 */
class JetWhaleHostNegotiationResponseSerializationTest : JetWhaleSerializationTest() {

    @Test
    fun `protocol version accept response should be serialized stably`() {
        val response = JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Accept(
            version = JetWhaleProtocolVersion(1),
        )

        val encoded = json.encodeToString(response)

        assertEquals(
            expected = """{"type":"negotiation/host/protocol_version_response/accept","version":1}""",
            actual = encoded,
        )
    }

    @Test
    fun `protocol version reject response should be serialized stably`() {
        val response = JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Reject(
            reason = "unsupported",
            supportedVersions = listOf(
                JetWhaleProtocolVersion(1),
                JetWhaleProtocolVersion(2),
            ),
        )

        val encoded = json.encodeToString(response)

        assertEquals(
            expected =
            """{"type":"negotiation/host/protocol_version_response/reject","reason":"unsupported","supportedVersions":[1,2]}""",
            actual = encoded,
        )
    }

    @Test
    fun `accept session response should be serialized stably`() {
        val response = JetWhaleHostNegotiationResponse.AcceptSession(
            sessionId = "session-123",
        )

        val encoded = json.encodeToString(response)

        assertEquals(
            expected = """{"type":"negotiation/host/accept_session","sessionId":"session-123"}""",
            actual = encoded,
        )
    }

    @Test
    fun `capabilities response should be serialized stably`() {
        val response = JetWhaleHostNegotiationResponse.CapabilitiesResponse(
            capabilities = mapOf("feature1" to "enabled", "feature2" to "disabled"),
        )

        val encoded = json.encodeToString(response)

        assertEquals(
            expected = """{"type":"negotiation/host/capabilities_response","capabilities":{"feature1":"enabled","feature2":"disabled"}}""",
            actual = encoded,
        )
    }

    @Test
    fun `capabilities response should be deserializable`() {
        val jsonString = """{"type":"negotiation/host/capabilities_response","capabilities":{"key":"value"}}"""

        val decoded = json.decodeFromString<JetWhaleHostNegotiationResponse>(jsonString)

        val capabilities = assertIs<JetWhaleHostNegotiationResponse.CapabilitiesResponse>(decoded)
        assertEquals(mapOf("key" to "value"), capabilities.capabilities)
    }

    @Test
    fun `available plugins response should be serialized stably`() {
        val response = JetWhaleHostNegotiationResponse.AvailablePluginsResponse(
            availablePlugins = listOf(
                JetWhalePluginInfo(
                    pluginId = "plugin-a",
                    pluginVersion = "1.0.0",
                ),
            ),
            incompatiblePlugins = listOf(
                JetWhalePluginInfo(
                    pluginId = "plugin-b",
                    pluginVersion = "0.9.0",
                ),
            ),
        )

        val encoded = json.encodeToString(response)

        assertEquals(
            expected = """{"type":"negotiation/host/available_plugins_response","availablePlugins":[{"type":"model/plugin_info","pluginId":"plugin-a","pluginVersion":"1.0.0"}],"incompatiblePlugins":[{"type":"model/plugin_info","pluginId":"plugin-b","pluginVersion":"0.9.0"}]}""",
            actual = encoded,
        )
    }

    @Test
    fun `protocol version accept response should be deserializable`() {
        val jsonString = """{"type":"negotiation/host/protocol_version_response/accept","version":1}"""

        val decoded = json.decodeFromString<JetWhaleHostNegotiationResponse>(jsonString)

        val accept = assertIs<JetWhaleHostNegotiationResponse.ProtocolVersionResponse.Accept>(decoded)

        assertEquals(1, accept.version.version)
    }
}
