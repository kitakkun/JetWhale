package com.kitakkun.jetwhale.host.data

import com.kitakkun.jetwhale.host.data.server.KtorWebSocketServer
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

class KtorWebSocketServerTest {
    @Test
    fun test() {
        val server = KtorWebSocketServer(
            json = Json,
            negotiationStrategy = mock(),
        )
        runBlocking {
            server.start("localhost", 5080)
        }
    }
}
