package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.McpActivity
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
        val id = repository.toolInvocationStarted("plugin.click", "com.example.plugin", "session-1")
        repository.toolInvocationFinished(id, failed = false)

        val record = repository.activityFlow.value.recentCalls.single()
        assertEquals("plugin.click", record.toolName)
        assertEquals("com.example.plugin", record.pluginId)
        assertEquals("session-1", record.sessionId)
        assertTrue(record.succeeded)
    }

    @Test
    fun `a call is only recorded once it finishes`() {
        repository.toolInvocationStarted("plugin.click", "com.example.plugin", "session-1")

        assertTrue(repository.activityFlow.value.recentCalls.isEmpty())
    }

    @Test
    fun `a failed call is recorded as unsuccessful`() {
        val id = repository.toolInvocationStarted("plugin.click", "com.example.plugin", "session-1")
        repository.toolInvocationFinished(id, failed = true)

        assertFalse(repository.activityFlow.value.recentCalls.single().succeeded)
    }

    @Test
    fun `history is ordered newest first`() {
        listOf("first", "second", "third").forEach { toolName ->
            val id = repository.toolInvocationStarted(toolName, "com.example.plugin", "session-1")
            repository.toolInvocationFinished(id, failed = false)
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
            val id = repository.toolInvocationStarted("tool-$index", "com.example.plugin", "session-1")
            repository.toolInvocationFinished(id, failed = false)
        }

        val recentCalls = repository.activityFlow.value.recentCalls
        assertEquals(McpActivity.MAX_RECENT_CALLS, recentCalls.size)
        assertEquals("tool-${overflow - 1}", recentCalls.first().toolName)
        assertEquals("tool-${overflow - McpActivity.MAX_RECENT_CALLS}", recentCalls.last().toolName)
    }

    @Test
    fun `finishing an unknown invocation records nothing`() {
        repository.toolInvocationFinished(invocationId = 404L, failed = false)

        assertTrue(repository.activityFlow.value.recentCalls.isEmpty())
    }

    @Test
    fun `clear drops the recorded history`() {
        val id = repository.toolInvocationStarted("plugin.click", "com.example.plugin", "session-1")
        repository.toolInvocationFinished(id, failed = false)

        repository.clear()

        assertTrue(repository.activityFlow.value.recentCalls.isEmpty())
    }
}
