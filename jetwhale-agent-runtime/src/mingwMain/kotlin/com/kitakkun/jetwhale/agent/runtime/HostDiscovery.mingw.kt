package com.kitakkun.jetwhale.agent.runtime

/**
 * mDNS host discovery is not implemented on Windows. Returns an empty list so the caller falls back
 * to the configured host.
 */
internal actual suspend fun browseJetWhaleServices(timeoutMillis: Long): List<DiscoveredService> {
    JetWhaleLogger.w("mDNS host discovery is not supported on this platform (Windows); using the configured host")
    return emptyList()
}
