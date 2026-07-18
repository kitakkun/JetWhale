package com.kitakkun.jetwhale.agent.runtime

/**
 * An interface representing the messaging service for communicating with the JetWhale debugger server.
 */
internal interface JetWhaleMessagingService {
    /**
     * Starts the messaging service, connecting to the first reachable host in [candidates].
     *
     * The candidates are tried in order on every (re)connection attempt, each with a short timeout,
     * so a stale or unreachable address falls through to the next quickly.
     *
     * @param candidates The ordered host addresses to try; must be non-empty.
     */
    fun startService(candidates: List<HostCandidate>)
}
