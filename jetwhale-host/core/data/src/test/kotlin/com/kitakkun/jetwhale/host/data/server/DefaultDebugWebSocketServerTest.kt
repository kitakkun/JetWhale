package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.AdbAutoPortMappingService
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.PluginSessionReconciliationService
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultDebugWebSocketServerTest {
    private val adbAutoPortMappingService = FakeAdbAutoPortMappingService()

    private val server = DefaultDebugWebSocketServer(
        adbAutoPortMappingService = adbAutoPortMappingService,
        sessionRepository = mock(MockMode.autoUnit),
        pluginInstanceService = mock(MockMode.autoUnit),
        settingsRepository = mock<DebuggerSettingsRepository> {
            everySuspend { readAdbAutoPortMappingEnabled() } returns true
        },
        reconciliationService = mock<PluginSessionReconciliationService> {
            every { reconciliationEvents() } returns emptyFlow()
        },
        ktorWebSocketServer = KtorWebSocketServer(json = Json, negotiationStrategy = mock(), sslCertificateManager = mock()),
        hostDiscoveryAdvertiser = mock(MockMode.autoUnit),
    )

    // Removing a mapping is slower than the server takes to stop, so a stop() that does not wait for
    // the removal returns before it.
    @Suppress("KOTRAIL_TEST_REAL_TIME_WAIT")
    @Test
    fun `stopping the server removes the adb port mappings before it returns`() = runBlocking {
        val port = ServerSocket(0).use(ServerSocket::getLocalPort)
        server.start(host = "localhost", port = port, wssPort = null)
        withTimeout(MAPPING_TIMEOUT_MILLIS) { adbAutoPortMappingService.mappedPorts.first { port in it } }

        server.stop()

        assertEquals(emptySet(), adbAutoPortMappingService.mappedPorts.value)
    }

    private class FakeAdbAutoPortMappingService : AdbAutoPortMappingService {
        val mappedPorts = MutableStateFlow(emptySet<Int>())

        override fun startPortMapping(port: Int) {
            mappedPorts.update { it + port }
        }

        override suspend fun stopPortMapping(port: Int) {
            delay(UNMAPPING_DURATION_MILLIS)
            mappedPorts.update { it - port }
        }
    }

    private companion object {
        const val MAPPING_TIMEOUT_MILLIS = 10_000L
        const val UNMAPPING_DURATION_MILLIS = 2_000L
    }
}
