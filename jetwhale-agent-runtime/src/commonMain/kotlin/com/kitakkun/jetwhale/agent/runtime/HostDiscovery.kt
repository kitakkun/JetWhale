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

/** What one browse attempt yielded. */
internal sealed interface DiscoveryResult {
    /** The network was browsed and these instances resolved within the timeout — possibly none. */
    data class Browsed(val services: List<DiscoveredService>) : DiscoveryResult

    /**
     * Browsing could not be performed at all, either because the platform has no mDNS backend or
     * because the backend could not be reached. [reason] completes the sentence "mDNS host discovery
     * is unavailable because …".
     */
    data class Unavailable(val reason: String) : DiscoveryResult
}

/**
 * Browses the local network for JetWhale debug servers advertised over mDNS/DNS-SD, returning every
 * instance resolved within [timeoutMillis].
 */
internal expect suspend fun browseJetWhaleServices(timeoutMillis: Long): DiscoveryResult

/** A discovered host paired with the port to reach it on. */
internal data class SelectedHost(val service: DiscoveredService, val port: Int)

/**
 * Picks the host to connect to among [services], or null when none can serve this agent.
 *
 * A host qualifies only when it satisfies every configured filter **and** advertises the port for the
 * scheme the connection will use. The scheme comes from the `ssl {}` block alone, so an ssl-configured
 * agent cannot use a host that advertises no wss port: dialling `wss://` at its plain-ws port would
 * fail every handshake. Such a host is skipped rather than chosen, leaving the next match a chance.
 */
internal fun selectHost(services: List<DiscoveredService>, discovery: HostDiscoveryConfig): SelectedHost? = services.asSequence()
    .filter { it.matches(discovery) }
    .mapNotNull { service -> service.portFor(discovery.useWss)?.let { SelectedHost(service, it) } }
    .firstOrNull()

/**
 * Browses for a JetWhale host over mDNS, standing [fallback] in whenever a browse yields none that
 * this agent can use.
 *
 * Browsing on every call is the point: a host started after the app, or one that came back on a
 * different port, is only reached by looking again. That makes the outcome repetitive by nature, so
 * the resolver remembers what it last reported and stays quiet while nothing changes.
 */
internal class MdnsEndpointResolver(
    val discovery: HostDiscoveryConfig,
    val fallback: ResolvedEndpoint,
) : EndpointResolver {
    private var lastReported: String? = null

    override suspend fun resolve(): ResolvedEndpoint {
        val result = browseJetWhaleServices(HOST_DISCOVERY_TIMEOUT_MILLIS)
        return when (result) {
            is DiscoveryResult.Unavailable -> {
                report("mDNS host discovery is unavailable because ${result.reason}; using $fallback", warn = true)
                fallback
            }

            is DiscoveryResult.Browsed -> {
                val selected = selectHost(result.services, discovery)
                if (selected == null) {
                    report(noHostMessage(result.services), warn = true)
                    fallback
                } else {
                    val ambiguous = !discovery.hasFilter && result.services.size > 1
                    report(chosenMessage(selected, result.services, ambiguous), warn = ambiguous)
                    ResolvedEndpoint(selected.service.address, selected.port)
                }
            }
        }
    }

    private fun report(message: String, warn: Boolean) {
        if (message == lastReported) return
        lastReported = message
        if (warn) JetWhaleLogger.w(message) else JetWhaleLogger.i(message)
    }

    private fun noHostMessage(services: List<DiscoveredService>): String {
        val matched = services.filter { it.matches(discovery) }
        if (matched.isEmpty()) {
            return "mDNS host discovery found no matching host within ${HOST_DISCOVERY_TIMEOUT_MILLIS}ms; using $fallback"
        }
        // Matched but unusable is worth spelling out: the agent would otherwise look like it simply
        // found nothing, when in fact the host is there and only its wss connector is missing.
        val scheme = if (discovery.useWss) "wss" else "ws"
        val listed = matched.joinToString { it.displayName() }
        return "mDNS host discovery matched ${matched.size} host(s) ($listed) but none advertised a $scheme port; using $fallback. " +
            "A host advertises its wss port only while wss is enabled in its settings."
    }

    private fun chosenMessage(selected: SelectedHost, services: List<DiscoveredService>, ambiguous: Boolean): String {
        val chosen = "Discovered JetWhale host over mDNS: ${selected.service.displayName()} at ${selected.service.address}:${selected.port}"
        // With no filter, first-match is ambiguous when several hosts advertise; make that visible
        // instead of silently picking one.
        if (!ambiguous) return chosen
        val listed = services.joinToString { "${it.displayName()}@${it.address}" }
        return "$chosen — but ${services.size} hosts advertised and no filter was set. Discovered: $listed. " +
            "Narrow with discovered(fallback) { allowHostName(...) } or allowAddress(...)."
    }
}

private fun DiscoveredService.displayName(): String = advertisedHostName ?: instanceName

private fun DiscoveredService.matches(discovery: HostDiscoveryConfig): Boolean {
    // hostName allowlist: exact, case-insensitive, compared against the advertised hostname (falling
    // back to the instance name when the host advertised no hostName TXT record).
    if (discovery.hostNames.isNotEmpty() && discovery.hostNames.none { displayName().equals(it, ignoreCase = true) }) {
        return false
    }
    // address allowlist: the resolved address must be one of the configured addresses.
    if (discovery.addresses.isNotEmpty() && address !in discovery.addresses) {
        return false
    }
    return true
}

private fun DiscoveredService.portFor(useWss: Boolean): Int? = if (useWss) wssPort else wsPort
