package com.kitakkun.jetwhale.plugins.deeplinks.agent

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenURLOptionUniversalLinksOnly
import kotlin.coroutines.resume

actual fun DeepLinkOpener.Companion.platformDefault(): DeepLinkOpener = IosDeepLinkOpener

private object IosDeepLinkOpener : DeepLinkOpener {
    override val canOpen: Boolean get() = true

    override suspend fun open(url: String): DeepLinkOpenResult {
        val nsUrl = NSURL.URLWithString(url) ?: return DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = "'$url' is not a valid URL")
        // Without universal-links-only, iOS opens an http(s) link in Safari; with it, the link goes
        // to the app that claims the domain, or the call fails.
        val options: Map<Any?, *> = if (nsUrl.scheme == "https" || nsUrl.scheme == "http") mapOf(UIApplicationOpenURLOptionUniversalLinksOnly to true) else emptyMap<Any?, Any>()
        val opened = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                UIApplication.sharedApplication.openURL(nsUrl, options = options) { success -> continuation.resume(success) }
            }
        }
        return DeepLinkOpenResult(
            opened = opened,
            handledBy = emptyList(),
            error = if (opened) null else "iOS did not open $url; no app, this one included, handles it",
        )
    }
}
