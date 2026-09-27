package com.kitakkun.jetwhale.plugins.deeplinks.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DEEP_LINKS_PLUGIN_ID
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@OptIn(ExperimentalJetWhaleApi::class)
internal class OpenDeepLinkCommand(
    private val client: DeepLinkClient,
) : JetWhaleMcpCommand() {
    override val name = "$DEEP_LINKS_PLUGIN_ID.openDeepLink"
    override val description =
        "Opens a link in the app the way the platform would when it is followed from outside the app. Reports whether it opened, which screens handled it (Android activities), and which of the app's declared links it matches, so a link that no declaration covers is visible before it fails on the device."

    private val url by string("The link to open, e.g. demo://item/42 or https://example.com/item/42.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val link = arguments[url]
        val declared = client.catalog().declared
        // A link that is not even a URL is still sent: the platform's own refusal is the answer.
        val matched = try {
            declarationsMatching(link, declared)
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        val result = client.open(link)
        return buildJsonObject {
            put("opened", result.opened)
            putJsonArray("handledBy") { result.handledBy.forEach { add(it) } }
            result.error?.let { put("error", it) }
            putJsonArray("matchesDeclared") { matched.forEach { add(it.handler) } }
        }.toString()
    }
}
