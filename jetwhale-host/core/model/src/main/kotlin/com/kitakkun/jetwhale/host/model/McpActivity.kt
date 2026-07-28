package com.kitakkun.jetwhale.host.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A single MCP tool call that is currently being executed on behalf of an AI agent.
 *
 * [pluginId] and [sessionId] are read from the tool call arguments when the tool declares them, so
 * calls that drive a plugin UI (click, type, scroll, drag, ...) can be attributed to what they are
 * operating on. Tools without those arguments leave them null.
 */
data class McpToolInvocation(
    val id: Long,
    val toolName: String,
    val pluginId: String?,
    val sessionId: String?,
)

/**
 * Live view of what AI agents are doing through the MCP server, so the UI can tell the user when
 * something other than themselves is driving the debugger.
 */
data class McpActivity(
    val connectedClientCount: Int,
    val runningInvocations: ImmutableList<McpToolInvocation>,
    /**
     * Monotonically increasing count of tool calls started. A tool call can begin and end faster
     * than the UI samples [runningInvocations], so that list is often empty between rapid calls and
     * can't be sampled reliably. Watching this counter change instead never misses a call, because
     * the value differs across frames even when individual increments are skipped.
     */
    val startedCount: Long,
    /** The most recently started tool call, kept so the UI can name and attribute it. */
    val lastStartedInvocation: McpToolInvocation?,
) {
    val hasConnectedClient: Boolean get() = connectedClientCount > 0

    companion object {
        val Idle = McpActivity(
            connectedClientCount = 0,
            runningInvocations = persistentListOf(),
            startedCount = 0,
            lastStartedInvocation = null,
        )
    }
}
