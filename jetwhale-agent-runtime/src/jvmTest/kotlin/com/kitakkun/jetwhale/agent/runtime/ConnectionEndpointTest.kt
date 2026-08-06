package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConnectionEndpointTest {
    private fun connection(
        configure: JetWhaleConnectionConfigurationScope.() -> Unit,
    ): EndpointResolver = JetWhaleConnectionConfiguration().apply(configure).endpointResolver()

    private fun fixedEndpoint(
        configure: JetWhaleConnectionConfigurationScope.() -> Unit,
    ): ResolvedEndpoint = runBlocking { assertIs<FixedEndpointResolver>(connection(configure)).resolve().single() }

    private fun mdns(
        configure: JetWhaleConnectionConfigurationScope.() -> Unit,
    ): MdnsEndpointResolver = assertIs<MdnsEndpointResolver>(connection(configure))

    @Test
    fun `an unconfigured connection targets the default host and port`() {
        assertEquals(ResolvedEndpoint("localhost", 8080, useWss = false), fixedEndpoint { })
    }

    @Suppress("DEPRECATION")
    @Test
    fun `the deprecated host and port amount to the same address as a fixed endpoint`() {
        val legacy = fixedEndpoint {
            host = "192.168.3.26"
            port = 5443
        }

        assertEquals(fixedEndpoint { endpoint = fixed("192.168.3.26", 5443) }, legacy)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `an assigned endpoint wins over the deprecated host and port`() {
        val resolved = fixedEndpoint {
            host = "ignored"
            port = 1
            endpoint = fixed("192.168.3.26", 5443)
        }

        assertEquals(ResolvedEndpoint("192.168.3.26", 5443, useWss = false), resolved)
    }

    @Test
    fun `a fixed endpoint follows the ssl block`() {
        val plain = fixedEndpoint { endpoint = fixed("192.168.3.26", 5080) }
        val secure = fixedEndpoint {
            endpoint = fixed("192.168.3.26", 5443)
            ssl { trustServerCertificate() }
        }

        assertEquals(false, plain.useWss)
        assertEquals(true, secure.useWss)
    }

    @Test
    fun `a plain loopback endpoint stays plain whatever the ssl block says`() {
        // The one exception to ssl {} deciding the scheme, and the reason it takes no host: plain text
        // is only safe because loopback cannot leave the machine.
        val resolved = fixedEndpoint {
            endpoint = plainLoopback(5080)
            ssl { trustServerCertificate() }
        }

        assertEquals(ResolvedEndpoint("localhost", 5080, useWss = false), resolved)
    }

    @Test
    fun `a plain loopback endpoint can be the fallback for discovery`() {
        // One shared configuration for every target: a physical device discovers a host over wss, and
        // anything that can only reach loopback — a browser above all — takes the plain fallback.
        val resolver = mdns {
            endpoint = discovered(fallback = plainLoopback(5080))
            ssl { trustServerCertificate() }
        }

        assertEquals(ResolvedEndpoint("localhost", 5080, useWss = false), resolver.fallback)
        assertEquals(true, resolver.discovery.useWss)
    }

    @Test
    fun `a discovered endpoint carries its filters and its fallback`() {
        val resolver = mdns {
            endpoint = discovered(fallback = fixed("localhost", 5443)) {
                allowHostName("build-machine")
                allowHostName("spare-machine")
                allowAddress("192.168.3.26")
                allowAddress("192.168.3.27")
            }
        }

        assertEquals(ResolvedEndpoint("localhost", 5443, useWss = false), resolver.fallback)
        // Both allowlists accumulate, so neither call silently drops the one before it.
        assertEquals(listOf("build-machine", "spare-machine"), resolver.discovery.hostNames)
        assertEquals(listOf("192.168.3.26", "192.168.3.27"), resolver.discovery.addresses)
    }

    @Test
    fun `a discovered endpoint without filters accepts any advertised host`() {
        val resolver = mdns { endpoint = discovered(fallback = fixed("localhost", 5443)) }

        assertEquals(emptyList(), resolver.discovery.hostNames)
        assertEquals(emptyList(), resolver.discovery.addresses)
        assertEquals(false, resolver.discovery.hasFilter)
    }

    @Test
    fun `ssl declared after the endpoint still uses wss`() {
        val resolver = mdns {
            endpoint = discovered(fallback = fixed("localhost", 5443))
            ssl { trustServerCertificate() }
        }

        assertEquals(true, resolver.discovery.useWss)
    }

    @Test
    fun `an endpoint without ssl uses plain ws`() {
        val resolver = mdns { endpoint = discovered(fallback = fixed("localhost", 5080)) }

        assertEquals(false, resolver.discovery.useWss)
    }
}
