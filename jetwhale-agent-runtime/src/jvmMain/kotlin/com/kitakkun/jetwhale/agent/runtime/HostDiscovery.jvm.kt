package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

private const val SERVICE_TYPE_LOCAL = "$JETWHALE_SERVICE_TYPE.local."

/**
 * JVM mDNS host discovery via jmDNS. Browses for `_jetwhale._tcp.local.` and returns every instance
 * resolved within the timeout window.
 */
internal actual suspend fun browseJetWhaleServices(timeoutMillis: Long): List<DiscoveredService> = withContext(Dispatchers.IO) {
    var jmdns: JmDNS? = null
    try {
        jmdns = JmDNS.create(InetAddress.getLocalHost())
        // Blocking browse: returns the services resolved within the timeout window.
        jmdns.list(SERVICE_TYPE_LOCAL, timeoutMillis).mapNotNull { it.toDiscoveredService() }
    } catch (e: Exception) {
        JetWhaleLogger.w("mDNS host discovery failed", e)
        emptyList()
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
