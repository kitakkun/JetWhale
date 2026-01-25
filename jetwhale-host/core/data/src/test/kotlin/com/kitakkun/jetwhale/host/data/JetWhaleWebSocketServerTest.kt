package com.kitakkun.jetwhale.host.data

import com.kitakkun.jetwhale.host.data.server.DefaultDebugWebSocketServer
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

class JetWhaleWebSocketServerTest {
    @Test
    fun test() {
        val server = DefaultDebugWebSocketServer(
            json = Json.Default,
            negotiationStrategy = mock(),
            adbAutoWiringService = mock(),
            pluginsRepository = mock(),
            sessionRepository = mock(),
            settingsRepository = mock(),
        )
        runBlocking {
            server.start("localhost", 5080)
        }
    }
}
