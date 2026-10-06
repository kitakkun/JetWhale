package com.kitakkun.jetwhale.plugins.mainthread.host

import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings

/**
 * The agent as the UI and the MCP commands reach it; the host plugin is the only real
 * implementation. Each call throws `JetWhaleMessagingException` when the app cannot be reached.
 */
internal interface MainThreadClient {
    suspend fun report(): MainThreadReport

    suspend fun updateSettings(settings: MonitorSettings): MonitorSettings

    suspend fun reset(): MainThreadReport
}
