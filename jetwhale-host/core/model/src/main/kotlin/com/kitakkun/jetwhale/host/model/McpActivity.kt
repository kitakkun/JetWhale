package com.kitakkun.jetwhale.host.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * One argument a tool call was made with, already rendered for display.
 *
 * [value] is the argument rendered to a string and shortened to [MAX_VALUE_LENGTH] characters, so a
 * call carrying a large body (a screenshot, an accessibility tree, ...) does not keep that body
 * alive for as long as the call stays in history.
 */
data class McpCallArgument(
    val name: String,
    val value: String,
) {
    companion object {
        /** How many characters of an argument value are kept. */
        const val MAX_VALUE_LENGTH = 80

        /** Appended to a [value] that was cut, so the UI does not have to mark it itself. */
        const val TRUNCATION_MARKER = "…"

        /** Builds an argument whose [value] is shortened to [MAX_VALUE_LENGTH] characters. */
        fun truncating(name: String, value: String): McpCallArgument = McpCallArgument(
            name = name,
            value = if (value.length <= MAX_VALUE_LENGTH) {
                value
            } else {
                value.take(MAX_VALUE_LENGTH) + TRUNCATION_MARKER
            },
        )
    }
}

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
    /** Every argument the call was made with, including `pluginId` and `sessionId`. */
    val arguments: ImmutableList<McpCallArgument>,
)

/**
 * A finished MCP tool call, kept so the UI can show what an agent already did.
 *
 * [finishedAtEpochMillis] is wall-clock time in epoch milliseconds, taken when the call completed.
 */
data class McpCallRecord(
    val id: Long,
    val toolName: String,
    val pluginId: String?,
    val sessionId: String?,
    val succeeded: Boolean,
    val finishedAtEpochMillis: Long,
    /** Every argument the call was made with, including `pluginId` and `sessionId`. */
    val arguments: ImmutableList<McpCallArgument>,
    /**
     * What the call produced, rendered to a string and shortened to [MAX_RESPONSE_LENGTH]. Carries
     * the failure message when the call failed, and is empty when there is nothing to show.
     */
    val response: String,
) {
    companion object {
        /**
         * How many characters of a [response] are kept. Far more generous than
         * [McpCallArgument.MAX_VALUE_LENGTH]: the response is the payload the user opens history to
         * read, whereas an argument only has to be recognisable.
         */
        const val MAX_RESPONSE_LENGTH = 2000

        /**
         * Shortens a response to [MAX_RESPONSE_LENGTH] characters, marking the cut the same way
         * argument values are marked.
         */
        fun truncateResponse(response: String): String = if (response.length <= MAX_RESPONSE_LENGTH) {
            response
        } else {
            response.take(MAX_RESPONSE_LENGTH) + McpCallArgument.TRUNCATION_MARKER
        }
    }
}

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
    /**
     * Tool calls that have already completed, newest first, capped at [MAX_RECENT_CALLS]. This is a
     * live troubleshooting aid rather than an audit log, so the oldest entries are dropped once the
     * cap is reached.
     */
    val recentCalls: ImmutableList<McpCallRecord>,
) {
    val hasConnectedClient: Boolean get() = connectedClientCount > 0

    companion object {
        /** How many completed calls [recentCalls] keeps before dropping the oldest. */
        const val MAX_RECENT_CALLS = 100

        val Idle = McpActivity(
            connectedClientCount = 0,
            runningInvocations = persistentListOf(),
            startedCount = 0,
            lastStartedInvocation = null,
            recentCalls = persistentListOf(),
        )
    }
}
