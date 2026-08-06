package com.kitakkun.jetwhale.agent.runtime

import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

/** RFC 5737 documentation address: never routable, but enough for the kernel to pick a source address. */
private const val ROUTE_PROBE_ADDRESS = "192.0.2.1"

/**
 * The address to speak mDNS on: the one the OS would use to reach the network, since that is where
 * anything looking for this machine is.
 *
 * `InetAddress.getLocalHost()` is not it. On a machine whose hostname resolves to loopback — the
 * default on macOS and common on Linux — it returns `127.0.0.1`, so the stack joins the multicast
 * group on `lo0` alone and neither hears nor is heard by anything on the network.
 *
 * One address, not one per interface: jmDNS gives each stack its own identity, so two stacks on the
 * same machine probe for the same name and declare a conflict against each other.
 *
 * @return null when the machine has no multicast-capable interface at all.
 */
internal fun primaryMulticastAddress(): Inet4Address? {
    val candidates = multicastInterfaceAddresses()
    val routed = routedSourceAddress()
    return candidates.firstOrNull { it == routed } ?: candidates.firstOrNull()
}

/** Every IPv4 address mDNS can usefully be spoken on: up, multicast-capable, not loopback or a tunnel. */
private fun multicastInterfaceAddresses(): List<Inet4Address> = Collections.list(NetworkInterface.getNetworkInterfaces())
    .filter { it.isUp && !it.isLoopback && !it.isPointToPoint && it.supportsMulticast() }
    .flatMap { Collections.list(it.inetAddresses) }
    .filterIsInstance<Inet4Address>()

/** The source address the OS would route from. Connecting a UDP socket sends nothing over the wire. */
private fun routedSourceAddress(): InetAddress? = try {
    DatagramSocket().use { socket ->
        socket.connect(InetAddress.getByName(ROUTE_PROBE_ADDRESS), 9)
        socket.localAddress
    }
} catch (e: Exception) {
    JetWhaleLogger.d("Could not determine the routed source address", e)
    null
}
