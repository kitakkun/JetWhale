package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.plugins.coroutines.protocol.ClearedLongRuns
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport

/**
 * The app's coroutines as the UI and the MCP commands reach them; the host plugin is the only real
 * implementation. Each call throws `JetWhaleMessagingException` when the app cannot be reached.
 */
internal interface CoroutineInspectorClient {
    suspend fun coroutineTree(): CoroutineTree

    suspend fun dispatcherStats(): DispatcherStatsReport

    suspend fun trackedFlows(): TrackedFlowReport

    suspend fun dump(): CoroutineDump

    suspend fun coroutineDetail(id: String): CoroutineDetail

    suspend fun clearLongRuns(): ClearedLongRuns
}
