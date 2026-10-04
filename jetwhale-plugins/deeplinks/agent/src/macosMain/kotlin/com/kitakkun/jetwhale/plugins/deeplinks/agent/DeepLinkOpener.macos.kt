package com.kitakkun.jetwhale.plugins.deeplinks.agent

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult
import platform.AppKit.NSWorkspace
import platform.Foundation.NSURL

actual fun DeepLinkOpener.Companion.platformDefault(): DeepLinkOpener = MacosDeepLinkOpener

private object MacosDeepLinkOpener : DeepLinkOpener {
    override val canOpen: Boolean get() = true

    override suspend fun open(url: String): DeepLinkOpenResult {
        val nsUrl = NSURL.URLWithString(url) ?: return DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = "'$url' is not a valid URL")
        // NSWorkspace hands the link to whichever app is registered for its scheme, which is this
        // app only when it is the scheme's registered handler.
        val opened = NSWorkspace.sharedWorkspace.openURL(nsUrl)
        return DeepLinkOpenResult(opened = opened, handledBy = emptyList(), error = if (opened) null else "macOS did not open $url")
    }
}
