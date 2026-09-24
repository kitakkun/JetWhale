package com.kitakkun.jetwhale.plugins.actions.agent

import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.serialization.Serializable
import java.awt.Desktop
import java.net.URI

actual fun DebugActionsBuilder.platformBuiltInActions() {
    group("Built-in") {
        action<OpenUrl>("Open URL") {
            description = "Opens a URL with the desktop's default handler — a browser, or the app registered for the scheme."
            run { args ->
                check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) { "this desktop cannot open URLs" }
                Desktop.getDesktop().browse(URI(args.url))
            }
        }
    }
}

@Serializable
private data class OpenUrl(
    @McpDescription("The URL to open.")
    val url: String,
)
