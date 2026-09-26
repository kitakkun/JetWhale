package com.kitakkun.jetwhale.agent.runtime

/** One place to look for the host, as declared in `endpoints { }`. */
internal sealed interface EndpointCandidate {
    data class Static(val host: String, val port: Int, val useWss: Boolean) : EndpointCandidate

    /** Always wss: the host serves plain ws on loopback, which discovery never returns. */
    data class Dynamic(
        val hostNames: List<String>,
        val addresses: List<String>,
        val acceptsAnyHost: Boolean,
    ) : EndpointCandidate
}

internal fun EndpointCandidate.resolver(): EndpointResolver = when (this) {
    is EndpointCandidate.Static -> FixedEndpointResolver(ResolvedEndpoint(host, port, useWss = useWss))

    is EndpointCandidate.Dynamic -> MdnsEndpointResolver(
        HostDiscoveryConfig(hostNames = hostNames, addresses = addresses, acceptsAnyHost = acceptsAnyHost),
    )
}

/**
 * Says so when a candidate will send in the clear to something that is not this machine.
 *
 * Not an error: `ws` is what the caller asked for, and a debug tool may take it. But plain text
 * leaving the machine is worth one line in the log rather than none. Discovered hosts cannot reach
 * this state — they are always dialled over wss — so only a written-out address is checked.
 */
internal fun EndpointCandidate.warnIfPlainOffMachine() {
    if (this !is EndpointCandidate.Static || useWss || isLoopbackHost(host)) return
    JetWhaleLogger.w("Configured to connect to $host:$port in the clear — plain text will leave this machine.")
}

/** RFC 6761 reserves `localhost` for the loopback interface, so the name is as good as the addresses. */
private fun isLoopbackHost(host: String): Boolean = host == "localhost" || host == "::1" || host.startsWith("127.")
