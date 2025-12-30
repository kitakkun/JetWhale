package com.kitakkun.jetwhale.debugger.host.data

import com.kitakkun.jetwhale.debugger.host.data.server.DefaultDebugWebSocketServer
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

class JetWhaleWebSocketServerTest {
    @Test
    fun test() {
        val server = DefaultDebugWebSocketServer(json = Json.Default, sessionRepository = mock())
        runBlocking {
            server.start("localhost", 5080)
        }
    }
}