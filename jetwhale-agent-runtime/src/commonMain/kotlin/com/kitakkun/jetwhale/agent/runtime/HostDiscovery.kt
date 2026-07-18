package com.kitakkun.jetwhale.agent.runtime

/** DNS-SD service type the host advertises the debug server under. */
internal const val JETWHALE_SERVICE_TYPE: String = "_jetwhale._tcp"

/** TXT record key for the plain-ws port. */
internal const val TXT_KEY_WS_PORT: String = "wsPort"

/** TXT record key for the wss port (absent when the host has wss disabled). */
internal const val TXT_KEY_WSS_PORT: String = "wssPort"

/**
 * TXT record key for the host machine's hostname. Carried separately from the mDNS instance name
 * because the instance name can be uniquified on collision (e.g. "name (2)"), whereas this stays the
 * raw hostname that `hostName` filtering compares against.
 */
internal const val TXT_KEY_HOST_NAME: String = "hostName"

/** How long host discovery browses before giving up and falling back to the configured host. */
internal const val HOST_DISCOVERY_TIMEOUT_MILLIS: Long = 5_000L

/** A JetWhale debug server instance discovered and resolved over mDNS/DNS-SD. */
internal data class DiscoveredService(
    /** The mDNS instance name (possibly uniquified by collision handling). */
    val instanceName: String,
    /** The host machine's hostname from the TXT records, or null when not advertised. */
    val advertisedHostName: String?,
    /** The resolved IP address. */
    val address: String,
    /** The advertised plain-ws port, or null. */
    val wsPort: Int?,
    /** The advertised wss port, or null when the host has wss disabled. */
    val wssPort: Int?,
)

/**
 * Browses the local network for JetWhale debug servers advertised over mDNS/DNS-SD and returns every
 * instance resolved within [timeoutMillis]. Returns an empty list when none are found or the platform
 * does not support mDNS discovery.
 */
internal expect suspend fun browseJetWhaleServices(timeoutMillis: Long): List<DiscoveredService>

/**
 * Resolves the effective host/port, preferring an mDNS-discovered host and falling back to
 * [fallbackHost]/[fallbackPort] when discovery is disabled or finds no matching host in time.
 */
internal suspend fun resolveHost(
    fallbackHost: String,
    fallbackPort: Int,
    discovery: HostDiscoveryConfig?,
): Pair<String, Int> {
    if (discovery == null) return fallbackHost to fallbackPort

    val services = browseJetWhaleServices(HOST_DISCOVERY_TIMEOUT_MILLIS)
    val matches = services.filter { it.matches(discovery) }

    if (matches.isEmpty()) {
        JetWhaleLogger.w("mDNS host discovery found no matching host within ${HOST_DISCOVERY_TIMEOUT_MILLIS}ms; falling back to $fallbackHost:$fallbackPort")
        return fallbackHost to fallbackPort
    }

    // With no filter, first-match can be ambiguous when several hosts advertise on the network; make
    // the ambiguity visible instead of silently picking one.
    if (!discovery.hasFilter && matches.size > 1) {
        val listed = matches.joinToString { "${it.displayName()}@${it.address}" }
        JetWhaleLogger.w("mDNS discovery found ${matches.size} JetWhale hosts and no filter was set; using the first. Discovered: $listed. Constrain with discoverHost { hostName(...) } or address(...).")
    }

    val chosen = matches.first()
    val port = chosen.selectPort(discovery.preferWss)
    if (port == null) {
        JetWhaleLogger.w("Discovered host ${chosen.displayName()} advertised no usable port; falling back to $fallbackHost:$fallbackPort")
        return fallbackHost to fallbackPort
    }
    JetWhaleLogger.i("Discovered JetWhale host over mDNS: ${chosen.displayName()} at ${chosen.address}:$port")
    return chosen.address to port
}

private fun DiscoveredService.displayName(): String = advertisedHostName ?: instanceName

private fun DiscoveredService.matches(discovery: HostDiscoveryConfig): Boolean {
    // hostName filter: exact, case-insensitive, compared against the advertised hostname (falling
    // back to the instance name when the host advertised no hostName TXT record).
    if (discovery.hostName != null && !displayName().equals(discovery.hostName, ignoreCase = true)) {
        return false
    }
    // address allowlist: the resolved address must be one of the configured addresses.
    if (discovery.addresses.isNotEmpty() && address !in discovery.addresses) {
        return false
    }
    return true
}

private fun DiscoveredService.selectPort(preferWss: Boolean): Int? = if (preferWss) wssPort ?: wsPort else wsPort ?: wssPort
