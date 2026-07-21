package com.kitakkun.jetwhale.agent.runtime

/**
 * An interface representing the messaging service for communicating with the JetWhale debugger server.
 */
internal interface JetWhaleMessagingService {
    /**
     * Starts the messaging service to connect to the JetWhale debugger server.
     *
     * @param host The hostname or IP address of the JetWhale debugger server, used when [discovery]
     *   is null or finds no advertised host in time.
     * @param port The port number of the JetWhale debugger server, used alongside [host] as the
     *   fallback.
     * @param discovery When non-null, mDNS host discovery is attempted before connecting and its
     *   result overrides [host]/[port]; when null, [host]/[port] are used directly.
     */
    fun startService(host: String, port: Int, discovery: HostDiscoveryConfig?)
}

/**
 * Configures mDNS host discovery for the agent.
 *
 * @property hostName When non-null, only a host whose advertised hostname matches this (exact,
 *   case-insensitive) is selected.
 * @property addresses When non-empty, only a host resolving to one of these IP addresses is selected.
 * @property preferWss Whether to prefer the advertised wss port (true when `ssl {}` is configured).
 */
internal data class HostDiscoveryConfig(
    val hostName: String?,
    val addresses: List<String>,
    val preferWss: Boolean,
) {
    /** True when at least one selection filter narrows the discovered hosts. */
    val hasFilter: Boolean get() = hostName != null || addresses.isNotEmpty()
}
