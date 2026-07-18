package com.kitakkun.jetwhale.agent.runtime

/**
 * Builds the ordered list of host addresses the agent tries when connecting, deduplicated by
 * host+port.
 *
 * Ordering:
 * - When the app set an explicit host, that address wins and is tried first, then the build-injected
 *   candidates, then the localhost fallback.
 * - Otherwise (no connection block, or only a port set), the build-injected candidates are tried
 *   first — the zero-config path for a physical device reaching the build machine — followed by the
 *   localhost fallback for emulators/simulators and ADB-forwarded devices.
 */
internal fun buildHostCandidates(
    configuredHost: String,
    configuredPort: Int,
    hostExplicitlySet: Boolean,
): List<HostCandidate> {
    val injected = JetWhaleBuildEnvironment.candidates(configuredPort)
    val localhost = HostCandidate(LOCALHOST, configuredPort, SOURCE_LOCALHOST)

    val ordered = if (hostExplicitlySet) {
        buildList {
            add(HostCandidate(configuredHost, configuredPort, SOURCE_CONFIGURED))
            addAll(injected)
            add(localhost)
        }
    } else {
        injected + localhost
    }

    return ordered.distinctBy { it.host to it.port }
}

private const val LOCALHOST = "localhost"
private const val SOURCE_CONFIGURED = "configured"
private const val SOURCE_LOCALHOST = "localhost-fallback"
