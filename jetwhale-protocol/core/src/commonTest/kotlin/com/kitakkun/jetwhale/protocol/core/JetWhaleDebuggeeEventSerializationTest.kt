package com.kitakkun.jetwhale.protocol.core

import com.kitakkun.jetwhale.protocol.JetWhaleSerializationTest
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for ensuring stable serialization of [JetWhaleDebuggeeEvent].
 */
class JetWhaleDebuggeeEventSerializationTest : JetWhaleSerializationTest() {

    @Test
    fun `notification frame message round-trips and carries the agent frame discriminator`() {
        val event = JetWhaleDebuggeeEvent.PluginFrameMessage(
            frame = PluginFrame.Notification(
                pluginId = "example-plugin",
                messageType = "network/request_sent",
                payload = "{}",
            ),
        )

        val encoded = json.encodeToString(event)
        assertTrue(encoded.contains(""""type":"event/agent/plugin_frame""""), "missing discriminator: $encoded")

        val decoded = json.decodeFromString<JetWhaleDebuggeeEvent>(encoded)
        assertEquals(event, assertIs<JetWhaleDebuggeeEvent.PluginFrameMessage>(decoded))
    }

    @Test
    fun `reply success frame message round-trips`() {
        val event = JetWhaleDebuggeeEvent.PluginFrameMessage(
            frame = PluginFrame.Reply.Success(
                pluginId = "example-plugin",
                inReplyTo = "corr-1",
                payload = "{}",
            ),
        )

        val decoded = json.decodeFromString<JetWhaleDebuggeeEvent>(json.encodeToString(event))
        assertEquals(event, decoded)
    }

    @Test
    fun `reply failure frame message round-trips`() {
        val event = JetWhaleDebuggeeEvent.PluginFrameMessage(
            frame = PluginFrame.Reply.Failure(
                pluginId = "example-plugin",
                inReplyTo = "corr-2",
                errorMessage = "something went wrong",
            ),
        )

        val decoded = json.decodeFromString<JetWhaleDebuggeeEvent>(json.encodeToString(event))
        assertEquals(event, decoded)
    }
}
