package com.kitakkun.jetwhale.agent.runtime

/** mDNS host discovery is out of reach on JS/Wasm, so the caller uses the configured host. */
internal actual suspend fun browseJetWhaleServices(timeoutMillis: Long): DiscoveryResult = DiscoveryResult.Unavailable("the browser sandbox has no multicast access")
