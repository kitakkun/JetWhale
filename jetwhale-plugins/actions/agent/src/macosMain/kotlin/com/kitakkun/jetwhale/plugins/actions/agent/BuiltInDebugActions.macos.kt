package com.kitakkun.jetwhale.plugins.actions.agent

import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.serialization.Serializable
import platform.AppKit.NSWorkspace
import platform.Foundation.NSURL

actual fun DebugActionsBuilder.platformBuiltInActions() {
    group("Built-in") {
        action<OpenUrl>("Open URL") {
            description = "Opens a URL with the Mac's default handler for its scheme."
            run { args ->
                val url = checkNotNull(NSURL.URLWithString(args.url)) { "'${args.url}' is not a URL" }
                check(NSWorkspace.sharedWorkspace.openURL(url)) { "no application opened '${args.url}'" }
            }
        }
    }
}

@Serializable
private data class OpenUrl(
    @McpDescription("The URL to open.")
    val url: String,
)
