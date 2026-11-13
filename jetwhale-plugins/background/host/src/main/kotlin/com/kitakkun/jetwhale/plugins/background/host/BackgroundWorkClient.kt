package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkSnapshot
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkOperationResult

/**
 * The app's background work as the UI and the MCP commands reach it; the host plugin is the only
 * real implementation. Each call throws `JetWhaleMessagingException` when the app cannot be reached.
 */
internal interface BackgroundWorkClient {
    suspend fun snapshot(): BackgroundWorkSnapshot

    suspend fun cancel(source: String, target: CancelTarget): WorkOperationResult

    suspend fun runNow(source: String, id: String): WorkOperationResult
}
