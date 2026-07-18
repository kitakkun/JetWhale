package com.kitakkun.jetwhale.agent.runtime

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import kotlin.coroutines.resume

/**
 * Android mDNS host discovery via [NsdManager]. The application [Context] is obtained reflectively
 * (`ActivityThread.currentApplication()`) so the discovery DSL needs no Context wiring from the app.
 *
 * Every instance found and resolved within the timeout window is returned; resolves are serialized
 * because older API levels reject a resolve while another is in flight.
 */
internal actual suspend fun browseJetWhaleServices(timeoutMillis: Long): List<DiscoveredService> {
    val context = currentApplicationContext()
    if (context == null) {
        JetWhaleLogger.w("mDNS host discovery unavailable: could not obtain the application Context; using the configured host")
        return emptyList()
    }
    val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    if (nsdManager == null) {
        JetWhaleLogger.w("mDNS host discovery unavailable: NsdManager not present; using the configured host")
        return emptyList()
    }

    val results = Collections.synchronizedList(mutableListOf<DiscoveredService>())
    // A single-slot serialized resolve queue: NsdManager rejects concurrent resolveService calls on
    // older API levels.
    val pending = ArrayDeque<NsdServiceInfo>()
    var resolving = false

    withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine<Unit> { continuation ->
            fun resolveNext(nsdManager: NsdManager, listener: NsdManager.ResolveListener) {
                synchronized(pending) {
                    if (resolving) return
                    val next = pending.removeFirstOrNull() ?: return
                    resolving = true
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(next, listener)
                }
            }

            lateinit var resolveListener: NsdManager.ResolveListener
            resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    JetWhaleLogger.d("mDNS resolve failed for ${serviceInfo.serviceName} (error=$errorCode)")
                    synchronized(pending) { resolving = false }
                    resolveNext(nsdManager, resolveListener)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    serviceInfo.toDiscoveredService()?.let { results.add(it) }
                    synchronized(pending) { resolving = false }
                    resolveNext(nsdManager, resolveListener)
                }
            }

            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    synchronized(pending) { pending.addLast(serviceInfo) }
                    resolveNext(nsdManager, resolveListener)
                }
            }

            nsdManager.discoverServices(JETWHALE_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            continuation.invokeOnCancellation {
                try {
                    nsdManager.stopServiceDiscovery(discoveryListener)
                } catch (e: Exception) {
                    JetWhaleLogger.d("Failed to stop mDNS discovery", e)
                }
            }
        }
    }

    return results.toList()
}

private fun NsdServiceInfo.toDiscoveredService(): DiscoveredService? {
    val address = host?.hostAddress ?: return null
    return DiscoveredService(
        instanceName = serviceName,
        advertisedHostName = attributes[TXT_KEY_HOST_NAME]?.decodeToString(),
        address = address,
        wsPort = attributes[TXT_KEY_WS_PORT]?.decodeToString()?.toIntOrNull(),
        wssPort = attributes[TXT_KEY_WSS_PORT]?.decodeToString()?.toIntOrNull(),
    )
}

/** Obtains the current application [Context] reflectively so no Context has to be passed in. */
private fun currentApplicationContext(): Context? = try {
    Class.forName("android.app.ActivityThread")
        .getMethod("currentApplication")
        .invoke(null) as? Context
} catch (e: Exception) {
    JetWhaleLogger.d("Reflective Context lookup failed", e)
    null
}
