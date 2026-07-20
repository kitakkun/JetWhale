package com.kitakkun.jetwhale.host.data.discovery

import com.kitakkun.jetwhale.host.model.DebugServerStatusProvider
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultHostDiscoveryAdvertiserTest {
    private class FakeStatusProvider : DebugServerStatusProvider {
        val mutableStatusFlow: MutableStateFlow<DebugWebSocketServerStatus> =
            MutableStateFlow(DebugWebSocketServerStatus.Stopped)
        override val statusFlow: StateFlow<DebugWebSocketServerStatus> = mutableStatusFlow
    }

    private class FakeMdnsRegistrar : MdnsRegistrar {
        val registrations: MutableList<Triple<String, Int, Int?>> = mutableListOf()
        var unregisterCount: Int = 0
        var closeCount: Int = 0

        override fun register(instanceName: String, wsPort: Int, wssPort: Int?) {
            registrations.add(Triple(instanceName, wsPort, wssPort))
        }

        override fun unregister() {
            unregisterCount++
        }

        override fun close() {
            closeCount++
        }
    }

    @Test
    fun `registers on Started and unregisters on Stopped`() = runBlocking {
        val statusProvider = FakeStatusProvider()
        val registrar = FakeMdnsRegistrar()
        val advertiser = DefaultHostDiscoveryAdvertiser(statusProvider, registrar)

        advertiser.start()

        statusProvider.mutableStatusFlow.value = DebugWebSocketServerStatus.Started("localhost", 8080, 8443)
        awaitUntil { registrar.registrations.size == 1 }
        assertEquals(8080, registrar.registrations.last().second)
        assertEquals(8443, registrar.registrations.last().third)

        statusProvider.mutableStatusFlow.value = DebugWebSocketServerStatus.Stopped
        awaitUntil { registrar.unregisterCount == 1 }

        // stop() must fully close the mDNS stack (not just unregister) so it does not leak.
        advertiser.stop()
        awaitUntil { registrar.closeCount == 1 }
    }

    @Test
    fun `re-registers only when the advertised ports change`() = runBlocking {
        val statusProvider = FakeStatusProvider()
        val registrar = FakeMdnsRegistrar()
        val advertiser = DefaultHostDiscoveryAdvertiser(statusProvider, registrar)

        advertiser.start()

        statusProvider.mutableStatusFlow.value = DebugWebSocketServerStatus.Started("localhost", 8080, null)
        awaitUntil { registrar.registrations.size == 1 }

        // Re-emitting an equivalent Started must not re-register.
        statusProvider.mutableStatusFlow.value = DebugWebSocketServerStatus.Started("localhost", 8080, null)
        delay(200)
        assertEquals(1, registrar.registrations.size)

        // A wss port appearing (e.g. certificate loaded) must re-register.
        statusProvider.mutableStatusFlow.value = DebugWebSocketServerStatus.Started("localhost", 8080, 8443)
        awaitUntil { registrar.registrations.size == 2 }
        assertEquals(8443, registrar.registrations.last().third)

        advertiser.stop()
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(20)
        }
        assertTrue(condition())
    }
}
