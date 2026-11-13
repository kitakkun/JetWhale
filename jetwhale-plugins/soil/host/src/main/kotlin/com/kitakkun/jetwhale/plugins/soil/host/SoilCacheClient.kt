package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheSnapshot
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionResult
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue

/**
 * The app's Soil cache as the UI and the MCP commands reach it; the host plugin is the only real
 * implementation. Each call throws `JetWhaleMessagingException` when the app cannot be reached.
 */
internal interface SoilCacheClient {
    suspend fun takeSnapshot(): SoilCacheSnapshot

    suspend fun readValue(handle: String): SoilEntryValue

    suspend fun runAction(handle: String, action: SoilEntryAction): SoilEntryActionResult
}
