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
    // Atomic because this list crosses threads: the delegate appends from the main run loop, while
    // the read at the end happens on whatever thread the coroutine resumed on once the timeout
    // expired — and the browser is still live at that moment, since cancellation only *dispatches*
    // the stop to the main queue. A plain MutableList would be a data race; Android's actual uses a
    // synchronized list for the same reason. Appends are single-writer (the main queue alone), so
    // read-then-store needs no CAS loop — only the visibility the atomic provides.
    val results = AtomicReference(emptyList<DiscoveredService>())
    // Strong references kept for the whole browse so the delegates and services outlive the enclosing
    // frame; their callbacks fire asynchronously on the run loop.
    val resolvingServices = mutableListOf<NSNetService>()

    withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine<Unit> { continuation ->
            // browser is created asynchronously on the main queue, and cancellation also stops it on
            // the main queue. Both mutate these fields only from that single serial queue, so creation
            // and stop can never race: whichever runs first wins, and the other observes the result
            // (a browser created after cancellation is stopped immediately; a cancellation before
            // creation flips [cancelled] so no browser is ever started).
            var browser: NSNetServiceBrowser? = null
            var cancelled = false

            val serviceDelegate = object : NSObject(), NSNetServiceDelegateProtocol {
                override fun netServiceDidResolveAddress(sender: NSNetService) {
                    sender.toDiscoveredService()?.let { results.value = results.value + it }
                }

                override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
                    JetWhaleLogger.d("mDNS resolve failed for ${sender.name}")
                }
            }

            val browserDelegate = object : NSObject(), NSNetServiceBrowserDelegateProtocol {
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

            dispatch_async(dispatch_get_main_queue()) {
                // Already cancelled before we got scheduled: do not start a browser that would leak.
                if (cancelled) return@dispatch_async
                browser = NSNetServiceBrowser().apply {
                    delegate = browserDelegate
                    searchForServicesOfType(SERVICE_TYPE_DOT, inDomain = SEARCH_DOMAIN)
                }
            }

            continuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    cancelled = true
                    // No-op when the browser was never created (cancelled before init).
                    browser?.stop()
                    browser = null
                }
            }
        }
    }

    return DiscoveryResult.Browsed(results.value)
}

private fun NSNetService.toDiscoveredService(): DiscoveredService? {
    // The resolved IPv4 address, not [hostName]. The host's certificate carries its addresses as IP
    // SANs, and the mDNS host name is whatever the advertising stack synthesised — jmDNS names its
    // host record after the address, e.g. "192-168-3-9.local." — which no certificate covers, so
    // dialling it fails hostname verification. Using the address also makes `allowAddress` mean what
    // it says on this platform.
    val address = resolvedIpv4Address() ?: return null
    val txt = TXTRecordData()?.let { NSNetService.dictionaryFromTXTRecordData(it) }
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
    val data = this as? platform.Foundation.NSData ?: return null
    @Suppress("CAST_NEVER_SUCCEEDS")
    return NSString.create(data, NSUTF8StringEncoding) as? String
}
