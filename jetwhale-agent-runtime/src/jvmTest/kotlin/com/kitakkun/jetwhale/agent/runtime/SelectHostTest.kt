package com.kitakkun.jetwhale.agent.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun service(
    hostName: String,
    address: String,
    wsPort: Int?,
    wssPort: Int?,
) = DiscoveredService(
    instanceName = hostName,
    advertisedHostName = hostName,
    address = address,
    wsPort = wsPort,
    wssPort = wssPort,
)

private fun discovery(
    hostName: String? = null,
    addresses: List<String> = emptyList(),
    useWss: Boolean = false,
) = HostDiscoveryConfig(hostName = hostName, addresses = addresses, useWss = useWss)

class SelectHostTest {
    private val plainOnly = service("plain-host", "192.168.3.26", wsPort = 5080, wssPort = null)
    private val bothPorts = service("secure-host", "192.168.3.27", wsPort = 5080, wssPort = 5443)

    @Test
    fun `no advertised host means nothing to select`() {
        assertNull(selectHost(emptyList(), discovery()))
    }

    @Test
    fun `a plain connection takes the advertised ws port`() {
        assertEquals(5080, selectHost(listOf(bothPorts), discovery(useWss = false))?.port)
    }

    @Test
    fun `an ssl connection takes the advertised wss port`() {
        assertEquals(5443, selectHost(listOf(bothPorts), discovery(useWss = true))?.port)
    }

    @Test
    fun `an ssl connection skips a host that advertises no wss port`() {
        // Dialling wss at the plain-ws port would fail every handshake, so the host is not a candidate
        // at all rather than a candidate reached on the wrong port.
        assertNull(selectHost(listOf(plainOnly), discovery(useWss = true)))
    }

    @Test
    fun `an ssl connection passes over a plain host to reach one that serves wss`() {
        val selected = selectHost(listOf(plainOnly, bothPorts), discovery(useWss = true))

        assertEquals("192.168.3.27", selected?.service?.address)
        assertEquals(5443, selected?.port)
    }

    @Test
    fun `hostName is matched case-insensitively`() {
        assertEquals("192.168.3.26", selectHost(listOf(plainOnly, bothPorts), discovery(hostName = "PLAIN-HOST"))?.service?.address)
    }

    @Test
    fun `a hostName that matches nothing selects nothing`() {
        assertNull(selectHost(listOf(plainOnly, bothPorts), discovery(hostName = "absent-host")))
    }

    @Test
    fun `the address allowlist admits only hosts resolving to one of its entries`() {
        assertEquals("192.168.3.27", selectHost(listOf(plainOnly, bothPorts), discovery(addresses = listOf("192.168.3.27")))?.service?.address)
        assertNull(selectHost(listOf(plainOnly, bothPorts), discovery(addresses = listOf("10.0.0.1"))))
    }

    @Test
    fun `hostName and address must both match when both are set`() {
        assertNull(selectHost(listOf(plainOnly), discovery(hostName = "plain-host", addresses = listOf("10.0.0.1"))))
        assertEquals(
            "192.168.3.26",
            selectHost(listOf(plainOnly), discovery(hostName = "plain-host", addresses = listOf("192.168.3.26")))?.service?.address,
        )
    }

    @Test
    fun `the instance name stands in when no hostName is advertised`() {
        val unnamed = DiscoveredService(
            instanceName = "fallback-name",
            advertisedHostName = null,
            address = "192.168.3.28",
            wsPort = 5080,
            wssPort = null,
        )

        assertEquals("192.168.3.28", selectHost(listOf(unnamed), discovery(hostName = "fallback-name"))?.service?.address)
    }
}
