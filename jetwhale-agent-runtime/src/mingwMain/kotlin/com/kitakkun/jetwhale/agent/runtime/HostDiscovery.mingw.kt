package com.kitakkun.jetwhale.agent.runtime

/** mDNS host discovery is not implemented on Windows, so the caller uses the configured host. */
internal actual suspend fun browseJetWhaleServices(timeoutMillis: Long): DiscoveryResult = DiscoveryResult.Unavailable("Windows has no mDNS backend")
