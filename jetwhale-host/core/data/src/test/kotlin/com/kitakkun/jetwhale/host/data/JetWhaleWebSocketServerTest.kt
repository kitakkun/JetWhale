package com.kitakkun.jetwhale.host.data

import com.kitakkun.jetwhale.host.data.server.DefaultDebugWebSocketServer
import com.kitakkun.jetwhale.host.data.server.KtorWebSocketServer
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class JetWhaleWebSocketServerTest {
    @Test
    fun test() {
        val server = DefaultDebugWebSocketServer(
            adbAutoWiringService = mock(),
            sessionRepository = mock(),
            pluginInstanceService = mock(),
            settingsRepository = mock(),
            enabledPluginsRepository = mock(),
            ktorWebSocketServer = mock<KtorWebSocketServer>(),
        )
        runBlocking {
            server.start("localhost", 5080)
        }
    }
}
