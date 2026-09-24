package com.kitakkun.jetwhale.plugins.actions.agent

import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.serialization.Serializable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun DebugActionsBuilder.platformBuiltInActions() {
    group("Built-in") {
        action<OpenUrl>("Open URL") {
            description = "Opens a URL the way the system would — a universal link or custom scheme can route back into this app."
            // UIApplication may only be used from the main thread.
            runsOnMainThread = true
            run { args ->
                val url = checkNotNull(NSURL.URLWithString(args.url)) { "'${args.url}' is not a URL" }
                UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
            }
        }
    }
}

@Serializable
private data class OpenUrl(
    @McpDescription("The URL to open.")
    val url: String,
)
