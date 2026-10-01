@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.kitakkun.jetwhale.agent.runtime

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.AF_INET
import kotlin.concurrent.AtomicReference

// NSNetServiceBrowser expects a fully qualified service type, trailing dot included.
private const val SERVICE_TYPE_DOT = "$JETWHALE_SERVICE_TYPE."
private const val SEARCH_DOMAIN = "local."
private const val RESOLVE_TIMEOUT_SECONDS = 5.0

// Darwin `sockaddr_in` layout: sa_len, sin_family, sin_port[2], sin_addr[4].
private const val SIN_FAMILY_OFFSET = 1
private const val SIN_ADDR_OFFSET = 4
private const val IPV4_OCTETS = 4
private const val IPV4_SOCKADDR_LENGTH = SIN_ADDR_OFFSET + IPV4_OCTETS

/**
 * Apple (iOS/macOS) mDNS host discovery via [NSNetServiceBrowser] + [NSNetService] resolution.
 *
 * The browser and its delegate callbacks run on the main run loop, so the browse is started via the
 * main dispatch queue and every instance resolved within the timeout window is collected. iOS
 * requires `_jetwhale._tcp` under `NSBonjourServices` in Info.plist, otherwise the OS silently blocks
 * the browse.
 */
internal actual suspend fun browseJetWhaleServices(timeoutMillis: Long): DiscoveryResult {
    val results = AtomicReference(emptyList<DiscoveredService>())
    val browse = MainQueueBrowse(onResolved = { results.value = results.value + it })

    withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine<Unit> { continuation ->
            dispatch_async(dispatch_get_main_queue(), browse::start)
            continuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue(), browse::stop)
            }
        }
    }

    return DiscoveryResult.Browsed(results.value)
}

/**
 * One browse for JetWhale hosts, from [start] until [stop].
 *
 * [NSNetServiceBrowser] and [NSNetService] keep their delegates unretained (`assign`), so the browse
 * holds the delegates, and the services it resolves, itself. Otherwise the garbage collector could
 * free a delegate mid-browse and leave the browser calling freed memory. [stop] clears those
 * pointers before letting go of them.
 *
 * Both run on the main queue only. Being serial, it orders them, so a [stop] that arrives before
 * [start] keeps the browse from ever starting.
 */
internal class MainQueueBrowse(private val onResolved: (DiscoveredService) -> Unit) {
    internal var browser: NSNetServiceBrowser? = null
        private set

    private var stopped = false

    private val resolvingServices = mutableListOf<NSNetService>()

    private val serviceDelegate = object : NSObject(), NSNetServiceDelegateProtocol {
        override fun netServiceDidResolveAddress(sender: NSNetService) {
            sender.toDiscoveredService()?.let(onResolved)
        }

        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
            JetWhaleLogger.d("mDNS resolve failed for ${sender.name}")
        }
    }

    private val browserDelegate = object : NSObject(), NSNetServiceBrowserDelegateProtocol {
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didFindService: NSNetService,
            moreComing: Boolean,
        ) {
            didFindService.delegate = serviceDelegate
            resolvingServices.add(didFindService)
            didFindService.resolveWithTimeout(RESOLVE_TIMEOUT_SECONDS)
        }

        override fun netServiceBrowserDidStopSearch(browser: NSNetServiceBrowser) = Unit
    }

    fun start() {
        if (stopped) return
        browser = NSNetServiceBrowser().apply {
            delegate = browserDelegate
            searchForServicesOfType(SERVICE_TYPE_DOT, inDomain = SEARCH_DOMAIN)
        }
    }

    fun stop() {
        stopped = true
        browser?.let {
            it.stop()
            it.delegate = null
        }
        browser = null
        resolvingServices.forEach {
            it.stop()
            it.delegate = null
        }
        resolvingServices.clear()
    }
}

private fun NSNetService.toDiscoveredService(): DiscoveredService? {
    // The resolved IPv4 address, not [hostName]: the host's certificate carries its addresses as IP
    // SANs, while the mDNS host name is whatever the advertising stack synthesized (jmDNS names its
    // record after the address, e.g. "192-168-3-9.local."), which no certificate covers, so dialing
    // it fails hostname verification. The address also makes `allowAddress` mean what it says on
    // this platform.
    val address = resolvedIpv4Address() ?: return null
    val txt = TXTRecordData()?.let(NSNetService::dictionaryFromTXTRecordData)
    return DiscoveredService(
        instanceName = name,
        advertisedHostName = txt?.get(TXT_KEY_HOST_NAME)?.toDecodedString(),
        address = address,
        wsPort = txt?.get(TXT_KEY_WS_PORT)?.toDecodedString()?.toIntOrNull(),
        wssPort = txt?.get(TXT_KEY_WSS_PORT)?.toDecodedString()?.toIntOrNull(),
    )
}

/**
 * The first IPv4 address this service resolved to, read out of the `sockaddr` blobs it carries.
 *
 * The bytes are read positionally rather than through the `sockaddr_in` bindings: the address is
 * already in network byte order there, so taking the four octets in place is both simpler and free of
 * any endianness assumption.
 */
@OptIn(UnsafeNumber::class)
private fun NSNetService.resolvedIpv4Address(): String? {
    val sockaddrs = addresses?.filterIsInstance<NSData>() ?: return null
    for (data in sockaddrs) {
        if (data.length.toInt() < IPV4_SOCKADDR_LENGTH) continue
        val bytes = data.bytes?.reinterpret<ByteVar>() ?: continue
        if (bytes[SIN_FAMILY_OFFSET].toInt() != AF_INET) continue
        return (0 until IPV4_OCTETS).joinToString(".") { bytes[SIN_ADDR_OFFSET + it].toUByte().toInt().toString() }
    }
    return null
}

/** TXT record values arrive as `NSData`; decode them as UTF-8 strings. */
private fun Any?.toDecodedString(): String? {
    val data = this as? NSData ?: return null
    // Kotlin/Native bridges NSString to String at runtime, a conversion the compiler cannot see.
    @Suppress("CAST_NEVER_SUCCEEDS")
    return NSString.create(data, NSUTF8StringEncoding) as? String
}
