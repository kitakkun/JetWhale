package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpServerStatus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the MCP server's own status, separately from the server that writes it.
 *
 * A host tool that wants to report the status cannot inject [McpServerService] itself: the server
 * is constructed from the set of tools, so the tool would close a dependency cycle. Both sides
 * depend on this holder instead.
 */
@Inject
@SingleIn(AppScope::class)
class McpServerStatusHolder {
    val statusFlow: StateFlow<McpServerStatus>
        field = MutableStateFlow<McpServerStatus>(McpServerStatus.Stopped)

    fun update(status: McpServerStatus) {
        statusFlow.value = status
    }
}
