package com.kitakkun.jetwhale.agent.runtime

/** A concrete address to dial — what resolution produces. */
internal data class ResolvedEndpoint(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
}

/**
 * Supplies the address for the next connection attempt.
 *
 * Asked before every attempt rather than once per session, so an address that only becomes correct
 * later is still reached without restarting the session.
 */
internal fun interface EndpointResolver {
    suspend fun resolve(): ResolvedEndpoint
}

/** Resolves to the same literal address every time. */
internal class FixedEndpointResolver(private val endpoint: ResolvedEndpoint) : EndpointResolver {
    override suspend fun resolve(): ResolvedEndpoint = endpoint
}
