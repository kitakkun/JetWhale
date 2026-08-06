package com.kitakkun.jetwhale.agent.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionEndpointTest {
    private fun connection(
        configure: JetWhaleConnectionConfigurationScope.() -> Unit,
    ): ResolvedConnectionTarget = JetWhaleConnectionConfiguration().apply(configure).resolveTarget()

    @Test
    fun `an unconfigured connection targets the default host and port`() {
        val target = connection { }

        assertEquals("localhost", target.host)
        assertEquals(8080, target.port)
        assertNull(target.discovery)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `the deprecated host and port amount to the same target as a fixed endpoint`() {
        val legacy = connection {
            host = "192.168.3.26"
            port = 5443
        }

        assertEquals(connection { endpoint = fixed("192.168.3.26", 5443) }, legacy)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `an assigned endpoint wins over the deprecated host and port`() {
        val target = connection {
            host = "ignored"
            port = 1
            endpoint = fixed("192.168.3.26", 5443)
        }

        assertEquals("192.168.3.26", target.host)
        assertEquals(5443, target.port)
    }

    @Test
    fun `a discovered endpoint carries its filters and falls back to its fixed endpoint`() {
        val target = connection {
            endpoint = discovered(fallback = fixed("localhost", 5443)) {
                matchHostName("build-machine")
                allowAddress("192.168.3.26")
                allowAddress("192.168.3.27")
            }
        }

        assertEquals("localhost", target.host)
        assertEquals(5443, target.port)
        assertEquals("build-machine", target.discovery?.hostName)
        assertEquals(listOf("192.168.3.26", "192.168.3.27"), target.discovery?.addresses)
    }

    @Test
    fun `a discovered endpoint without filters accepts any advertised host`() {
        val target = connection { endpoint = discovered(fallback = fixed("localhost", 5443)) }

        assertNull(target.discovery?.hostName)
        assertEquals(emptyList(), target.discovery?.addresses)
        assertEquals(false, target.discovery?.hasFilter)
    }

    @Test
    fun `ssl declared after the endpoint still uses wss`() {
        val target = connection {
            endpoint = discovered(fallback = fixed("localhost", 5443))
            ssl { trustServerCertificate() }
        }

        assertEquals(true, target.discovery?.useWss)
    }

    @Test
    fun `an endpoint without ssl uses plain ws`() {
        val target = connection { endpoint = discovered(fallback = fixed("localhost", 5080)) }

        assertEquals(false, target.discovery?.useWss)
    }
}
