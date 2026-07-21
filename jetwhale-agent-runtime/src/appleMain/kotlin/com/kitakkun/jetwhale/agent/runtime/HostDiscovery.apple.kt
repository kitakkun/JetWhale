@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.kitakkun.jetwhale.agent.runtime

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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

private const val SERVICE_TYPE_DOT = "$JETWHALE_SERVICE_TYPE."
private const val SEARCH_DOMAIN = "local."
private const val RESOLVE_TIMEOUT_SECONDS = 5.0

/**
 * Apple (iOS/macOS) mDNS host discovery via [NSNetServiceBrowser] + [NSNetService] resolution.
 *
 * The browser and its delegate callbacks run on the main run loop, so the browse is started via the
 * main dispatch queue and every instance resolved within the timeout window is collected. iOS
 * requires `_jetwhale._tcp` under `NSBonjourServices` in Info.plist, otherwise the OS silently blocks
 * the browse.
 */
internal actual suspend fun browseJetWhaleServices(timeoutMillis: Long): List<DiscoveredService> {
    val results = mutableListOf<DiscoveredService>()
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
                    sender.toDiscoveredService()?.let { results.add(it) }
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

    return results.toList()
}

private fun NSNetService.toDiscoveredService(): DiscoveredService? {
    val host = hostName ?: return null
    val txt = TXTRecordData()?.let { NSNetService.dictionaryFromTXTRecordData(it) }
    return DiscoveredService(
        instanceName = name,
        advertisedHostName = txt?.get(TXT_KEY_HOST_NAME)?.toDecodedString(),
        address = host.removeSuffix("."),
        wsPort = txt?.get(TXT_KEY_WS_PORT)?.toDecodedString()?.toIntOrNull(),
        wssPort = txt?.get(TXT_KEY_WSS_PORT)?.toDecodedString()?.toIntOrNull(),
    )
}

/** TXT record values arrive as `NSData`; decode them as UTF-8 strings. */
private fun Any?.toDecodedString(): String? {
    val data = this as? platform.Foundation.NSData ?: return null
    @Suppress("CAST_NEVER_SUCCEEDS")
    return NSString.create(data, NSUTF8StringEncoding) as? String
}
