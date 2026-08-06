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
 * @property hostNames When non-empty, only a host advertising one of these hostnames (exact,
 *   case-insensitive) is selected.
 * @property addresses When non-empty, only a host resolving to one of these IP addresses is selected.
 * @property useWss Whether the connection will use wss (true when `ssl {}` is configured). A host is
 *   usable only when it advertises the port for that scheme, since the scheme is not negotiable.
 */
internal data class HostDiscoveryConfig(
    val hostNames: List<String>,
    val addresses: List<String>,
    /** Set by `allowAll()`: take any host that advertises the service, allowlists or not. */
    val acceptsAnyHost: Boolean,
) {
    /** True when at least one allowlist narrows the discovered hosts. */
    val hasFilter: Boolean get() = hostNames.isNotEmpty() || addresses.isNotEmpty()

    /**
     * True when nothing at all was stated, so no host can be accepted.
     *
     * Discovery reaches every JetWhale host on the network, which on a shared one is other people's.
     * An empty block therefore takes nobody rather than everybody; `allowAll()` says otherwise.
     */
    val acceptsNothing: Boolean get() = !acceptsAnyHost && !hasFilter
}
