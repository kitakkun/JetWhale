package com.kitakkun.jetwhale.agent.runtime

/**
 * A single host address the agent may try when connecting to the debugger host.
 *
 * @property host The hostname or IP address.
 * @property port The port to connect on.
 * @property source A short label describing where the candidate came from, used only for logging.
 */
internal data class HostCandidate(
    val host: String,
    val port: Int,
    val source: String,
)

/**
 * Registry of host addresses captured at build time and injected into the app by the JetWhale Gradle
 * plugin, so `startJetWhale {}` can reach the build machine over the LAN with no connection block.
 *
 * The JetWhale Gradle plugin generates a small source file that calls [registerHostCandidates] with
 * the build machine's non-loopback IPv4 addresses and hostname. Because the addresses are captured
 * when the app is built, they are only correct as long as the build machine keeps the same addresses
 * — which is the norm for the common "same machine builds and debugs" workflow. When they go stale,
 * the agent simply falls through to the other candidates (explicit config, then localhost).
 *
 * Registration is additive and idempotent per address, so calling it more than once (e.g. from
 * multiple generated files) is safe.
 */
public object JetWhaleBuildEnvironment {
    private val mutableAddresses: MutableList<String> = mutableListOf()
    private var buildHostName: String? = null

    /**
     * Registers host addresses captured at build time.
     *
     * @param hostName The build machine's hostname, or null when unavailable.
     * @param addresses The build machine's non-loopback IPv4 addresses.
     */
    public fun registerHostCandidates(hostName: String?, addresses: List<String>) {
        if (hostName != null && buildHostName == null) buildHostName = hostName
        addresses.forEach { address ->
            if (address !in mutableAddresses) mutableAddresses.add(address)
        }
    }

    /** Clears all registered candidates. Intended for tests. */
    internal fun clear() {
        mutableAddresses.clear()
        buildHostName = null
    }

    /**
     * The build-injected candidates for [port], IPv4 addresses first (most reliable), then the
     * hostname as a last resort in case the LAN resolves it.
     */
    internal fun candidates(port: Int): List<HostCandidate> = buildList {
        mutableAddresses.forEach { add(HostCandidate(it, port, SOURCE_BUILD_ADDRESS)) }
        buildHostName?.let { add(HostCandidate(it, port, SOURCE_BUILD_HOSTNAME)) }
    }

    private const val SOURCE_BUILD_ADDRESS = "build-injected-address"
    private const val SOURCE_BUILD_HOSTNAME = "build-injected-hostname"
}
