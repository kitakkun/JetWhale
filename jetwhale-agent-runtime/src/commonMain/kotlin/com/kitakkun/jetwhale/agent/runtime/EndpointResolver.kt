package com.kitakkun.jetwhale.agent.runtime

/**
 * A concrete address to dial — what resolution produces.
 *
 * [useWss] travels with the address rather than being read from `ssl { }` at dial time: `ssl { }`
 * still decides it for every endpoint but one, and `plainLoopback` is that one.
 */
internal data class ResolvedEndpoint(val host: String, val port: Int, val useWss: Boolean) {
    override fun toString(): String = "${if (useWss) "wss" else "ws"}://$host:$port"
}

/**
 * Supplies the addresses worth trying, best first.
 *
 * Asked before every round of attempts rather than once per session, so an address that only becomes
 * correct later is still reached without restarting the session. Returning a list rather than one
 * address is what lets a host that answers discovery but refuses connections be passed over: the
 * caller works down the list and only gives up once every entry has been tried.
 */
internal fun interface EndpointResolver {
    suspend fun resolve(): List<ResolvedEndpoint>
}

/** Resolves to the same literal address every time. */
internal class FixedEndpointResolver(private val endpoint: ResolvedEndpoint) : EndpointResolver {
    override suspend fun resolve(): List<ResolvedEndpoint> = listOf(endpoint)
}

/**
 * Every declared candidate in the order it was declared, each contributing what it resolves to — one
 * address for a literal, however many answered for a discovered one, none where mDNS is unavailable.
 *
 * Deduplicated with the order kept: the same address reached two ways is one address, and dialling it
 * twice in a round would only spend the establishment budget twice.
 */
internal class CandidateListResolver(private val candidates: List<EndpointResolver>) : EndpointResolver {
    override suspend fun resolve(): List<ResolvedEndpoint> = candidates.flatMap { it.resolve() }.distinct()
}
