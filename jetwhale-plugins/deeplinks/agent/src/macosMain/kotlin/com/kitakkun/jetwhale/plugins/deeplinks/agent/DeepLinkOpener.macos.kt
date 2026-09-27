package com.kitakkun.jetwhale.plugins.deeplinks.agent

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult
import platform.AppKit.NSWorkspace
import platform.Foundation.NSURL

actual fun DeepLinkOpener.Companion.platformDefault(): DeepLinkOpener = MacosDeepLinkOpener

// NSWorkspace hands the link to whichever app is registered for it, which is this app only when it
// is the registered handler of the scheme.
private object MacosDeepLinkOpener : DeepLinkOpener {
    override val canOpen: Boolean get() = true

    override suspend fun open(url: String): DeepLinkOpenResult {
        val nsUrl = NSURL.URLWithString(url) ?: return DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = "'$url' is not a valid URL")
        val opened = NSWorkspace.sharedWorkspace.openURL(nsUrl)
        return DeepLinkOpenResult(opened = opened, handledBy = emptyList(), error = if (opened) null else "macOS did not open $url")
    }
}
