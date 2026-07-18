package com.kitakkun.jetwhale.host.model

/**
 * How secure the transport carrying a debug session is.
 *
 * @property TLS Encrypted wss connection; the strongest guarantee.
 * @property LOOPBACK Plain ws whose remote peer is a loopback address. Traffic never leaves the
 * machine, so it is effectively secure. This is the case for ADB-forwarded connections from a
 * device, where the forwarded socket terminates on localhost.
 * @property PLAINTEXT Plain ws to a non-loopback peer; the traffic is unencrypted on the network.
 */
enum class SessionTransportSecurity {
    TLS,
    LOOPBACK,
    PLAINTEXT,
}
