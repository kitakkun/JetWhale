package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory store of live MCP agent activity: how many MCP clients are connected, and which tool
 * calls are in flight right now.
 *
 * The MCP layer writes to it from a single choke point that wraps every tool handler; the UI reads
 * it to render the AI operation indicator.
 */
interface McpActivityRepository {
    val activityFlow: StateFlow<McpActivity>

    fun clientConnected()

    fun clientDisconnected()

    /**
     * Records the start of a tool call.
     *
     * @return the invocation id to hand back to [toolInvocationFinished] once the call completes.
     */
    fun toolInvocationStarted(toolName: String, pluginId: String?, sessionId: String?): Long

    fun toolInvocationFinished(invocationId: Long)

    /** Drops all recorded activity, so a server restart does not inherit stale connection counts. */
    fun clear()
}
