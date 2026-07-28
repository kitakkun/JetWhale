package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.McpActivity
import com.kitakkun.jetwhale.host.model.McpCallArgument
import com.kitakkun.jetwhale.host.model.McpCallRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultMcpActivityRepositoryTest {

    private val repository = DefaultMcpActivityRepository()

    @Test
    fun `no calls are recorded before anything runs`() {
        assertTrue(repository.activityFlow.value.recentCalls.isEmpty())
    }

    @Test
    fun `a completed call is recorded with its attribution`() {
        val id = repository.toolInvocationStarted("plugin.click", "com.example.plugin", "session-1", emptyMap())
        repository.toolInvocationFinished(id, failed = false, response = "")

        val record = repository.activityFlow.value.recentCalls.single()
        assertEquals("plugin.click", record.toolName)
        assertEquals("com.example.plugin", record.pluginId)
        assertEquals("session-1", record.sessionId)
        assertTrue(record.succeeded)
    }

    @Test
    fun `a call is only recorded once it finishes`() {
        repository.toolInvocationStarted("plugin.click", "com.example.plugin", "session-1", emptyMap())

        assertTrue(repository.activityFlow.value.recentCalls.isEmpty())
    }

    @Test
    fun `a failed call is recorded as unsuccessful`() {
        val id = repository.toolInvocationStarted("plugin.click", "com.example.plugin", "session-1", emptyMap())
        repository.toolInvocationFinished(id, failed = true, response = "")

        assertFalse(repository.activityFlow.value.recentCalls.single().succeeded)
    }

    @Test
    fun `history is ordered newest first`() {
        listOf("first", "second", "third").forEach { toolName ->
            val id = repository.toolInvocationStarted(toolName, "com.example.plugin", "session-1", emptyMap())
            repository.toolInvocationFinished(id, failed = false, response = "")
        }

        assertEquals(
            listOf("third", "second", "first"),
            repository.activityFlow.value.recentCalls.map { it.toolName },
        )
    }

    @Test
    fun `history drops the oldest calls once the cap is reached`() {
        val overflow = McpActivity.MAX_RECENT_CALLS + 5
        repeat(overflow) { index ->
            val id = repository.toolInvocationStarted("tool-$index", "com.example.plugin", "session-1", emptyMap())
            repository.toolInvocationFinished(id, failed = false, response = "")
        }

        val recentCalls = repository.activityFlow.value.recentCalls
        assertEquals(McpActivity.MAX_RECENT_CALLS, recentCalls.size)
        assertEquals("tool-${overflow - 1}", recentCalls.first().toolName)
        assertEquals("tool-${overflow - McpActivity.MAX_RECENT_CALLS}", recentCalls.last().toolName)
    }

    @Test
    fun `finishing an unknown invocation records nothing`() {
        repository.toolInvocationFinished(invocationId = 404L, failed = false, response = "")

        assertTrue(repository.activityFlow.value.recentCalls.isEmpty())
    }

    @Test
    fun `a completed call is recorded with the arguments it was made with`() {
        val id = repository.toolInvocationStarted(
            "plugin.click",
            "com.example.plugin",
            "session-1",
            mapOf("sessionId" to "session-1", "x" to "100"),
        )
        repository.toolInvocationFinished(id, failed = false, response = "")

        val record = repository.activityFlow.value.recentCalls.single()
        assertEquals(
            listOf(
                McpCallArgument("sessionId", "session-1"),
                McpCallArgument("x", "100"),
            ),
            record.arguments,
        )
    }

    @Test
    fun `a long argument value is truncated with an ellipsis`() {
        val value = "a".repeat(McpCallArgument.MAX_VALUE_LENGTH + 20)
        val id = repository.toolInvocationStarted(
            "plugin.type",
            "com.example.plugin",
            "session-1",
            mapOf("text" to value),
        )
        repository.toolInvocationFinished(id, failed = false, response = "")

        val recorded = repository.activityFlow.value.recentCalls.single().arguments.single()
        assertEquals(
            "a".repeat(McpCallArgument.MAX_VALUE_LENGTH) + McpCallArgument.TRUNCATION_MARKER,
            recorded.value,
        )
    }

    @Test
    fun `an argument value at the cap is kept whole`() {
        val value = "a".repeat(McpCallArgument.MAX_VALUE_LENGTH)
        val id = repository.toolInvocationStarted(
            "plugin.type",
            "com.example.plugin",
            "session-1",
            mapOf("text" to value),
        )
        repository.toolInvocationFinished(id, failed = false, response = "")

        assertEquals(value, repository.activityFlow.value.recentCalls.single().arguments.single().value)
    }

    @Test
    fun `a call without arguments records an empty argument list`() {
        val id = repository.toolInvocationStarted("plugin.screenshot", "com.example.plugin", "session-1", emptyMap())
        repository.toolInvocationFinished(id, failed = false, response = "")

        assertTrue(repository.activityFlow.value.recentCalls.single().arguments.isEmpty())
    }

    @Test
    fun `a completed call is recorded with the response it produced`() {
        val id = repository.toolInvocationStarted("plugin.screenshot", "com.example.plugin", "session-1", emptyMap())
        repository.toolInvocationFinished(id, failed = false, response = "<image>")

        assertEquals("<image>", repository.activityFlow.value.recentCalls.single().response)
    }

    @Test
    fun `a long response is truncated with an ellipsis`() {
        val response = "a".repeat(McpCallRecord.MAX_RESPONSE_LENGTH + 20)
        val id = repository.toolInvocationStarted("plugin.tree", "com.example.plugin", "session-1", emptyMap())
        repository.toolInvocationFinished(id, failed = false, response = response)

        assertEquals(
            "a".repeat(McpCallRecord.MAX_RESPONSE_LENGTH) + McpCallArgument.TRUNCATION_MARKER,
            repository.activityFlow.value.recentCalls.single().response,
        )
    }

    @Test
    fun `a response at the cap is kept whole`() {
        val response = "a".repeat(McpCallRecord.MAX_RESPONSE_LENGTH)
        val id = repository.toolInvocationStarted("plugin.tree", "com.example.plugin", "session-1", emptyMap())
        repository.toolInvocationFinished(id, failed = false, response = response)

        assertEquals(response, repository.activityFlow.value.recentCalls.single().response)
    }

    @Test
    fun `a call without a response records an empty response`() {
        val id = repository.toolInvocationStarted("plugin.click", "com.example.plugin", "session-1", emptyMap())
        repository.toolInvocationFinished(id, failed = false, response = "")

        assertEquals("", repository.activityFlow.value.recentCalls.single().response)
    }

    @Test
    fun `a failed call is recorded with the failure message as its response`() {
        val id = repository.toolInvocationStarted("plugin.click", "com.example.plugin", "session-1", emptyMap())
        repository.toolInvocationFinished(id, failed = true, response = "boom")

        val record = repository.activityFlow.value.recentCalls.single()
        assertFalse(record.succeeded)
        assertEquals("boom", record.response)
    }

    @Test
    fun `clear drops the recorded history`() {
        val id = repository.toolInvocationStarted("plugin.click", "com.example.plugin", "session-1", emptyMap())
        repository.toolInvocationFinished(id, failed = false, response = "")

        repository.clear()

        assertTrue(repository.activityFlow.value.recentCalls.isEmpty())
    }
}
