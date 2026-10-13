package com.kitakkun.jetwhale.agent.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

/** What `discoverWss { allowAll() }` amounts to, which most of these are about. */
private val anyHost = discovery(acceptsAnyHost = true)

private fun discovery(
    hostNames: List<String> = emptyList(),
    addresses: List<String> = emptyList(),
    acceptsAnyHost: Boolean = false,
) = HostDiscoveryConfig(hostNames = hostNames, addresses = addresses, acceptsAnyHost = acceptsAnyHost)

class SelectHostTest {
    private val plainOnly = service("plain-host", "192.168.3.26", wsPort = 5080, wssPort = null)
    private val secureHost = service("secure-host", "192.168.3.27", wsPort = 5080, wssPort = 5443)
    private val spareHost = service("spare-host", "192.168.3.28", wsPort = 5080, wssPort = 5444)

    @Test
    fun `no advertised host means nothing to select`() {
        assertEquals(emptyList(), selectHosts(emptyList(), anyHost))
    }

    @Test
    fun `a discovered host is taken at its advertised wss port`() {
        assertEquals(5443, selectHosts(listOf(secureHost), anyHost).firstOrNull()?.port)
    }

    @Test
    fun `a host advertising no wss port is skipped`() {
        assertEquals(emptyList(), selectHosts(listOf(plainOnly), anyHost))
    }

    @Test
    fun `a plain host is passed over to reach one that serves wss`() {
        val selected = selectHosts(listOf(plainOnly, secureHost), anyHost)

        assertEquals(1, selected.size)
        assertEquals("192.168.3.27", selected.single().service.address)
        assertEquals(5443, selected.single().port)
    }

    @Test
    fun `every usable host is offered, in browse order`() {
        val selected = selectHosts(listOf(secureHost, spareHost), anyHost)

        assertEquals(listOf("192.168.3.27", "192.168.3.28"), selected.map { it.service.address })
    }

    @Test
    fun `stating no policy at all selects nothing`() {
        assertEquals(emptyList(), selectHosts(listOf(secureHost, spareHost), discovery()))
    }

    @Test
    fun `an allowlist still applies when allowAll is stated alongside it`() {
        val contradictory = discovery(hostNames = listOf("secure-host"), acceptsAnyHost = true)

        assertEquals(listOf("192.168.3.27"), selectHosts(listOf(secureHost, spareHost), contradictory).map { it.service.address })
    }

    @Test
    fun `hostName is matched case-insensitively`() {
        assertEquals(
            "192.168.3.27",
            selectHosts(listOf(secureHost, spareHost), discovery(hostNames = listOf("SECURE-HOST"))).firstOrNull()?.service?.address,
        )
    }

    @Test
    fun `a hostName that matches nothing selects nothing`() {
        assertEquals(emptyList(), selectHosts(listOf(secureHost, spareHost), discovery(hostNames = listOf("absent-host"))))
    }

    @Test
    fun `the hostName allowlist admits a host matching any of its entries`() {
        val allowed = discovery(hostNames = listOf("absent-host", "spare-host"))

        assertEquals("192.168.3.28", selectHosts(listOf(secureHost, spareHost), allowed).firstOrNull()?.service?.address)
    }

    @Test
    fun `the address allowlist admits only hosts resolving to one of its entries`() {
        assertEquals(
            "192.168.3.28",
            selectHosts(listOf(secureHost, spareHost), discovery(addresses = listOf("192.168.3.28"))).firstOrNull()?.service?.address,
        )
        assertEquals(emptyList(), selectHosts(listOf(secureHost, spareHost), discovery(addresses = listOf("10.0.0.1"))))
    }

    @Test
    fun `hostName and address must both match when both are set`() {
        assertEquals(emptyList(), selectHosts(listOf(secureHost), discovery(hostNames = listOf("secure-host"), addresses = listOf("10.0.0.1"))))
        assertEquals(
            "192.168.3.27",
            selectHosts(listOf(secureHost), discovery(hostNames = listOf("secure-host"), addresses = listOf("192.168.3.27"))).firstOrNull()?.service?.address,
        )
    }

    @Test
    fun `the instance name stands in when no hostName is advertised`() {
        val unnamed = DiscoveredService(
            instanceName = "fallback-name",
            advertisedHostName = null,
            address = "192.168.3.29",
            wsPort = 5080,
            wssPort = 5443,
        )

        assertEquals("192.168.3.29", selectHosts(listOf(unnamed), discovery(hostNames = listOf("fallback-name"))).firstOrNull()?.service?.address)
    }
}

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
