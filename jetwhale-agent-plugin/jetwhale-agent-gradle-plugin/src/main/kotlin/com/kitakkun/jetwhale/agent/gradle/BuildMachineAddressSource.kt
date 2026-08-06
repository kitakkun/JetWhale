package com.kitakkun.jetwhale.agent.gradle

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * The build machine's address on the network a device could reach it at.
 *
 * A `ValueSource` rather than a plain configuration-time lookup: the address is external state that
 * changes between builds — moving between Wi-Fi and Ethernet, home and office — and this is the
 * mechanism that lets the configuration cache notice instead of serving yesterday's value.
 */
internal abstract class BuildMachineAddressSource : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String? = primaryAddress()
}

/**
 * The source address the OS would use to reach the wider network.
 *
 * `InetAddress.getLocalHost()` is not usable for this: on a machine whose hostname resolves through
 * `/etc/hosts` it answers `127.0.0.1`, which no device can reach. Connecting a UDP socket asks the
 * routing table instead — and because UDP connect sends nothing, the address need not exist.
 * [TEST_NET_1] is reserved by RFC 5737 for exactly this kind of use.
 */
private fun primaryAddress(): String? = runCatching {
    DatagramSocket().use { socket ->
        socket.connect(InetSocketAddress(InetAddress.getByName(TEST_NET_1), DISCARD_PORT))
        (socket.localAddress as? Inet4Address)
            ?.hostAddress
            ?.takeUnless { it == "0.0.0.0" || it.startsWith("127.") }
    }
}.getOrNull()

/** RFC 5737 TEST-NET-1: documentation-only, so nothing is ever actually addressed. */
private const val TEST_NET_1 = "192.0.2.1"
private const val DISCARD_PORT = 9
