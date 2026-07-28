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
     * @param arguments the arguments the call was made with, already rendered to strings. Values are
     * shortened to [McpCallArgument.MAX_VALUE_LENGTH] here, so nothing large is retained for as long
     * as the call stays in history.
     * @return the invocation id to hand back to [toolInvocationFinished] once the call completes.
     */
    fun toolInvocationStarted(
        toolName: String,
        pluginId: String?,
        sessionId: String?,
        arguments: Map<String, String>,
    ): Long

    /**
     * Records the completion of a tool call and appends it to the recent-call history.
     *
     * @param failed true when the handler threw instead of returning a result.
     */
    fun toolInvocationFinished(invocationId: Long, failed: Boolean)

    /** Drops all recorded activity, so a server restart does not inherit stale connection counts. */
    fun clear()
}
