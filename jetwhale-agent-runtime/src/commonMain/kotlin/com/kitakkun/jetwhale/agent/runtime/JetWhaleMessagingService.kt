package com.kitakkun.jetwhale.agent.runtime

/**
 * An interface representing the messaging service for communicating with the JetWhale debugger server.
 */
internal interface JetWhaleMessagingService {
    /**
     * Starts the messaging service to connect to the JetWhale debugger server.
     *
     * @param resolver Asked for an address before every connection attempt. How that address is
     *   arrived at — a literal one, or one browsed for over mDNS with a fallback — is the resolver's
     *   business, not the service's.
     */
    fun startService(resolver: EndpointResolver)

    /**
     * Stops the messaging service: the reconnect loop is torn down, the current connection is closed
     * and every plugin peer is dropped.
     *
     * Terminal — the service is not restartable afterwards. Returns as soon as the teardown is
     * scheduled, and repeated calls are ignored.
     */
    fun stopService()
}

/**
 * Which advertised host the agent will accept, and on which port.
 *
 * @property hostName When non-null, only a host whose advertised hostname matches this (exact,
 *   case-insensitive) is selected.
 * @property addresses When non-empty, only a host resolving to one of these IP addresses is selected.
 * @property useWss Whether the connection will use wss (true when `ssl {}` is configured). A host is
 *   usable only when it advertises the port for that scheme, since the scheme is not negotiable.
 */
internal data class HostDiscoveryConfig(
    val hostName: String?,
    val addresses: List<String>,
    val useWss: Boolean,
) {
    /** True when at least one selection filter narrows the discovered hosts. */
    val hasFilter: Boolean get() = hostName != null || addresses.isNotEmpty()
}
