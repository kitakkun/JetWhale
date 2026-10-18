package com.kitakkun.jetwhale.plugins.deeplinks.host

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkCatalog
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkTemplate

/** An app that declares [declared] and opens exactly the links one of them matches. */
internal class FakeDeepLinkClient(
    private val declared: List<DeclaredDeepLink>,
    private val templates: List<DeepLinkTemplate>,
) : DeepLinkClient {
    val opened = mutableListOf<String>()

    override suspend fun catalog(): DeepLinkCatalog = DeepLinkCatalog(declared = declared, templates = templates, canOpen = true, notes = emptyList())

    override suspend fun open(url: String): DeepLinkOpenResult {
        opened += url
        val handlers = try {
            declarationsMatching(url, declared).map(DeclaredDeepLink::handler)
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        return when {
            handlers.isEmpty() -> DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = "no activity of this app handles $url")
            else -> DeepLinkOpenResult(opened = true, handledBy = handlers, error = null)
        }
    }
}
