package com.kitakkun.jetwhale.plugins.deeplinks.host

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkCatalog
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult

/**
 * The app's deep links as the UI and the MCP commands reach them; the host plugin is the only real
 * implementation. Each call throws `JetWhaleMessagingException` when the app cannot be reached.
 */
internal interface DeepLinkClient {
    suspend fun catalog(): DeepLinkCatalog

    suspend fun open(url: String): DeepLinkOpenResult
}
