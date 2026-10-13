package com.kitakkun.jetwhale.agent.runtime

/** mDNS host discovery is not implemented on Linux, so the caller uses the configured host. */
internal actual suspend fun browseJetWhaleServices(timeoutMillis: Long): DiscoveryResult = DiscoveryResult.Unavailable("Linux has no mDNS backend")
