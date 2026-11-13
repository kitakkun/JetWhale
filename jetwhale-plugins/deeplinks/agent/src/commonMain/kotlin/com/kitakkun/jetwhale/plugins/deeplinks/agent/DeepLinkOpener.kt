package com.kitakkun.jetwhale.plugins.deeplinks.agent

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult

/** Opens a link in the app. Implement it to route links through the app's own navigation. */
interface DeepLinkOpener {
    /** False only for an opener that cannot open anything, so the host can say so up front. */
    val canOpen: Boolean

    suspend fun open(url: String): DeepLinkOpenResult

    companion object
}

/**
 * Opens a link the way the platform does when it is followed from outside the app: an `ACTION_VIEW`
 * intent restricted to the app on Android, `UIApplication.open` on iOS, `NSWorkspace` on macOS. On the
 * JVM and the web it cannot open anything; pass an opener of the app's own there.
 */
expect fun DeepLinkOpener.Companion.platformDefault(): DeepLinkOpener

/** For platforms with no way to route a link into the running app. */
internal class UnsupportedDeepLinkOpener(private val reason: String) : DeepLinkOpener {
    override val canOpen: Boolean get() = false

    override suspend fun open(url: String): DeepLinkOpenResult = DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = reason)
}
