package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

private const val SERVICE_TYPE_LOCAL = "$JETWHALE_SERVICE_TYPE.local."

/**
 * JVM mDNS host discovery via jmDNS. Browses for `_jetwhale._tcp.local.` and returns every instance
 * resolved within the timeout window.
 */
internal actual suspend fun browseJetWhaleServices(timeoutMillis: Long): DiscoveryResult = withContext(Dispatchers.IO) {
    val address = primaryMulticastAddress()
        ?: return@withContext DiscoveryResult.Unavailable("no multicast-capable network interface is available")

    var jmdns: JmDNS? = null
    try {
        jmdns = JmDNS.create(address)
        // Blocking browse: returns the services resolved within the timeout window.
        DiscoveryResult.Browsed(jmdns.list(SERVICE_TYPE_LOCAL, timeoutMillis).mapNotNull { it.toDiscoveredService() })
    } catch (e: Exception) {
        // The detail goes to debug; the caller reports the failure itself, deduplicated across the
        // retries that would otherwise repeat it forever.
        JetWhaleLogger.d("jmDNS browse failed", e)
        DiscoveryResult.Unavailable("the jmDNS browse failed (${e.message})")
    } finally {
        try {
            jmdns?.close()
        } catch (e: Exception) {
            JetWhaleLogger.d("Failed to close jmDNS", e)
        }
    }
}

private fun ServiceInfo.toDiscoveredService(): DiscoveredService? {
    val address = inet4Addresses.firstOrNull()?.hostAddress
        ?: hostAddresses.firstOrNull()
        ?: return null
    return DiscoveredService(
        instanceName = name,
        advertisedHostName = getPropertyString(TXT_KEY_HOST_NAME),
        address = address,
        wsPort = getPropertyString(TXT_KEY_WS_PORT)?.toIntOrNull(),
        wssPort = getPropertyString(TXT_KEY_WSS_PORT)?.toIntOrNull(),
    )
}
