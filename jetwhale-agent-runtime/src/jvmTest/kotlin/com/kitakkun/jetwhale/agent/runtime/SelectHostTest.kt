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
    hostNames: List<String> = emptyList(),
    addresses: List<String> = emptyList(),
    useWss: Boolean = false,
) = HostDiscoveryConfig(hostNames = hostNames, addresses = addresses, useWss = useWss)

class SelectHostTest {
    private val plainOnly = service("plain-host", "192.168.3.26", wsPort = 5080, wssPort = null)
    private val bothPorts = service("secure-host", "192.168.3.27", wsPort = 5080, wssPort = 5443)

    @Test
    fun `no advertised host means nothing to select`() {
        assertEquals(emptyList(), selectHosts(emptyList(), discovery()))
    }

    @Test
    fun `a plain connection takes the advertised ws port`() {
        assertEquals(5080, selectHosts(listOf(bothPorts), discovery(useWss = false)).firstOrNull()?.port)
    }

    @Test
    fun `an ssl connection takes the advertised wss port`() {
        assertEquals(5443, selectHosts(listOf(bothPorts), discovery(useWss = true)).firstOrNull()?.port)
    }

    @Test
    fun `an ssl connection skips a host that advertises no wss port`() {
        // Dialling wss at the plain-ws port would fail every handshake, so the host is not a candidate
        // at all rather than a candidate reached on the wrong port.
        assertEquals(emptyList(), selectHosts(listOf(plainOnly), discovery(useWss = true)))
    }

    @Test
    fun `an ssl connection passes over a plain host to reach one that serves wss`() {
        val selected = selectHosts(listOf(plainOnly, bothPorts), discovery(useWss = true))

        assertEquals(1, selected.size)
        assertEquals("192.168.3.27", selected.single().service.address)
        assertEquals(5443, selected.single().port)
    }

    @Test
    fun `every usable host is offered, in browse order`() {
        // Answering mDNS is not the same as accepting a connection, so the caller gets them all and
        // works down the list rather than being handed one and stranded if it refuses.
        val selected = selectHosts(listOf(plainOnly, bothPorts), discovery(useWss = false))

        assertEquals(listOf("192.168.3.26", "192.168.3.27"), selected.map { it.service.address })
    }

    @Test
    fun `hostName is matched case-insensitively`() {
        assertEquals("192.168.3.26", selectHosts(listOf(plainOnly, bothPorts), discovery(hostNames = listOf("PLAIN-HOST"))).firstOrNull()?.service?.address)
    }

    @Test
    fun `a hostName that matches nothing selects nothing`() {
        assertEquals(emptyList(), selectHosts(listOf(plainOnly, bothPorts), discovery(hostNames = listOf("absent-host"))))
    }

    @Test
    fun `the hostName allowlist admits a host matching any of its entries`() {
        val allowed = discovery(hostNames = listOf("absent-host", "secure-host"))

        assertEquals("192.168.3.27", selectHosts(listOf(plainOnly, bothPorts), allowed).firstOrNull()?.service?.address)
    }

    @Test
    fun `the address allowlist admits only hosts resolving to one of its entries`() {
        assertEquals("192.168.3.27", selectHosts(listOf(plainOnly, bothPorts), discovery(addresses = listOf("192.168.3.27"))).firstOrNull()?.service?.address)
        assertEquals(emptyList(), selectHosts(listOf(plainOnly, bothPorts), discovery(addresses = listOf("10.0.0.1"))))
    }

    @Test
    fun `hostName and address must both match when both are set`() {
        assertEquals(emptyList(), selectHosts(listOf(plainOnly), discovery(hostNames = listOf("plain-host"), addresses = listOf("10.0.0.1"))))
        assertEquals(
            "192.168.3.26",
            selectHosts(listOf(plainOnly), discovery(hostNames = listOf("plain-host"), addresses = listOf("192.168.3.26"))).firstOrNull()?.service?.address,
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

        assertEquals("192.168.3.28", selectHosts(listOf(unnamed), discovery(hostNames = listOf("fallback-name"))).firstOrNull()?.service?.address)
    }
}
