package com.kitakkun.jetwhale.host.model

/**
 * Advertises the running debug server on the local network via mDNS/DNS-SD (Bonjour) so agents on
 * physical devices can discover the host without hardcoding its LAN IP address.
 *
 * The advertiser tracks the debug server status: it registers a `_jetwhale._tcp` service while the
 * server is running and unregisters it when the server stops. Port changes re-register the service
 * with the updated TXT records.
 */
interface HostDiscoveryAdvertiser {
    /**
     * Begins tracking the debug server status and advertising it while it runs. Idempotent: calling
     * it again while already active has no effect.
     */
    fun start()

    /** Stops advertising and releases the underlying mDNS resources. */
    fun stop()
}
