package com.kitakkun.jetwhale.agent.runtime

/** A concrete address to dial — what resolution produces. */
internal data class ResolvedEndpoint(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
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
