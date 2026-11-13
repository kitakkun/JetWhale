package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration

@OptIn(InternalJetWhaleHostApi::class)
class JetWhaleMessagingHostPluginTest {
    @Test
    fun `messenger in the host session refuses, saying the host session has no agent`() {
        val plugin = MessengerProbePlugin().apply { bindSessionKind(isInHostSession = true) }

        val error = assertFailsWith<IllegalStateException>(block = plugin::readMessenger)

        assertContains(error.message.orEmpty(), "host session, which has no agent")
    }

    @Test
    fun `messenger in an app session is the one the host bound`() {
        val messenger = FakeMessenger()
        val plugin = MessengerProbePlugin().apply {
            bindSessionKind(isInHostSession = false)
            bindMessenger(messenger)
        }

        assertEquals(messenger, plugin.readMessenger())
    }

    private class MessengerProbePlugin : JetWhaleMessagingHostPlugin() {
        fun readMessenger(): JetWhaleMessenger = messenger
    }

    private class FakeMessenger : JetWhaleMessenger {
        override val payloadFormat: StringFormat = Json

        override fun sendRaw(messageType: String, payload: String): Boolean = true

        override suspend fun requestRaw(messageType: String, payload: String, timeout: Duration?): String = payload
    }
}
