package com.kitakkun.jetwhale.host.data.discovery

import com.kitakkun.jetwhale.host.model.DebugServerStatusProvider
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class DefaultHostDiscoveryAdvertiserTest {
    private class FakeStatusProvider : DebugServerStatusProvider {
        val mutableStatusFlow: MutableStateFlow<DebugWebSocketServerStatus> =
            MutableStateFlow(DebugWebSocketServerStatus.Stopped)
        override val statusFlow: StateFlow<DebugWebSocketServerStatus> = mutableStatusFlow
    }

    private sealed interface RegistrarCall {
        data class Register(val instanceName: String, val wsPort: Int, val wssPort: Int?) : RegistrarCall
        data object Unregister : RegistrarCall
        data object Close : RegistrarCall
    }

    /**
     * Records every call the advertiser makes, in order, so a test awaits the next one instead of
     * polling for a count to change.
     */
    private class FakeMdnsRegistrar : MdnsRegistrar {
        private val calls = Channel<RegistrarCall>(Channel.UNLIMITED)

        suspend fun nextCall(): RegistrarCall = withTimeout(5.seconds) { calls.receive() }

        override fun register(instanceName: String, wsPort: Int, wssPort: Int?) {
            calls.trySend(RegistrarCall.Register(instanceName, wsPort, wssPort))
        }

        override fun unregister() {
            calls.trySend(RegistrarCall.Unregister)
        }

        override fun close() {
            calls.trySend(RegistrarCall.Close)
        }
    }

    @Test
    fun `registers on Started and unregisters on Stopped`() = runBlocking {
        val statusProvider = FakeStatusProvider()
        val registrar = FakeMdnsRegistrar()
        val advertiser = DefaultHostDiscoveryAdvertiser(statusProvider, registrar)

        advertiser.start()

        statusProvider.mutableStatusFlow.value = DebugWebSocketServerStatus.Started("localhost", 8080, 8443)
        val registered = assertIs<RegistrarCall.Register>(registrar.nextCall())
        assertEquals(8080, registered.wsPort)
        assertEquals(8443, registered.wssPort)

        statusProvider.mutableStatusFlow.value = DebugWebSocketServerStatus.Stopped
        assertEquals(RegistrarCall.Unregister, registrar.nextCall())

        // stop() must fully close the mDNS stack (not just unregister) so it does not leak.
        advertiser.stop()
        assertEquals(RegistrarCall.Close, registrar.nextCall())
    }

    @Test
    fun `re-registers only when the advertised ports change`() = runBlocking {
        val statusProvider = FakeStatusProvider()
        val registrar = FakeMdnsRegistrar()
        val advertiser = DefaultHostDiscoveryAdvertiser(statusProvider, registrar)

        advertiser.start()

        statusProvider.mutableStatusFlow.value = DebugWebSocketServerStatus.Started("localhost", 8080, null)
        assertNull(assertIs<RegistrarCall.Register>(registrar.nextCall()).wssPort)

        // An equivalent Started must not re-register; a wss port appearing (e.g. certificate loaded)
        // must. The advertiser observes the two in order, so the next registration it makes is the
        // one carrying the wss port — a redundant one would arrive here instead.
        statusProvider.mutableStatusFlow.value = DebugWebSocketServerStatus.Started("localhost", 8080, null)
        statusProvider.mutableStatusFlow.value = DebugWebSocketServerStatus.Started("localhost", 8080, 8443)
        assertEquals(8443, assertIs<RegistrarCall.Register>(registrar.nextCall()).wssPort)

        advertiser.stop()
    }
}
