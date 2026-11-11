package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.AI_OPERATION_INDICATOR_LINGER
import com.kitakkun.jetwhale.host.model.McpActivity
import com.kitakkun.jetwhale.host.model.McpActivityRepository
import com.kitakkun.jetwhale.host.model.McpCallRecord
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import com.kitakkun.jetwhale.host.model.McpToolPermission
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRunningMcpCommandsServiceTest {
    private val activity = MutableStateFlow(McpActivity.Idle)
    private val activityRepository = mock<McpActivityRepository> {
        every { activityFlow } returns this@DefaultRunningMcpCommandsServiceTest.activity
    }

    @Test
    fun `a command an agent is running is reported`() = runTest {
        val emissions = collectRunningCommandNames()

        activity.value = McpActivity.Idle.copy(runningInvocations = persistentListOf(invocation(TAP_COMMAND_NAME)))

        assertEquals(setOf(TAP_COMMAND_NAME), emissions.last())
    }

    @Test
    fun `a command that finished before it was ever seen running is reported until the linger runs out`() = runTest {
        val emissions = collectRunningCommandNames()

        activity.value = McpActivity.Idle.copy(recentCalls = persistentListOf(finishedCall(TAP_COMMAND_NAME, finishedAtEpochMillis = nowEpochMillis())))
        advanceTimeBy(AI_OPERATION_INDICATOR_LINGER.inWholeMilliseconds - 1)
        assertEquals(setOf(TAP_COMMAND_NAME), emissions.last())

        advanceTimeBy(2)
        assertEquals(emptySet(), emissions.last())
    }

    @Test
    fun `each finished command drops when its own linger runs out`() = runTest {
        val emissions = collectRunningCommandNames()
        val tapFinishedAt = nowEpochMillis()
        val swipeFinishedAt = tapFinishedAt - 1_000

        activity.value = McpActivity.Idle.copy(recentCalls = persistentListOf(finishedCall(TAP_COMMAND_NAME, tapFinishedAt), finishedCall(SWIPE_COMMAND_NAME, swipeFinishedAt)))
        assertEquals(setOf(TAP_COMMAND_NAME, SWIPE_COMMAND_NAME), emissions.last())

        advanceTimeBy(AI_OPERATION_INDICATOR_LINGER.inWholeMilliseconds - 1_000 + 1)
        assertEquals(setOf(TAP_COMMAND_NAME), emissions.last())

        advanceTimeBy(1_000)
        assertEquals(emptySet(), emissions.last())
    }

    @Test
    fun `a command that finished longer ago than the linger is not reported`() = runTest {
        val emissions = collectRunningCommandNames()

        activity.value = McpActivity.Idle.copy(
            recentCalls = persistentListOf(finishedCall(TAP_COMMAND_NAME, finishedAtEpochMillis = nowEpochMillis() - AI_OPERATION_INDICATOR_LINGER.inWholeMilliseconds)),
        )

        assertEquals(listOf(emptySet()), emissions)
    }

    @Test
    fun `calls to another plugin, another session or a tool that is not the plugin's command are not reported`() = runTest {
        val emissions = collectRunningCommandNames()

        activity.value = McpActivity.Idle.copy(
            runningInvocations = persistentListOf(
                invocation(TAP_COMMAND_NAME, pluginId = "other-plugin"),
                invocation(TAP_COMMAND_NAME, sessionId = "other-session"),
                invocation("jetwhale.click"),
            ),
        )

        assertEquals(listOf(emptySet()), emissions)
    }

    private fun TestScope.collectRunningCommandNames(): List<Set<String>> {
        val service = DefaultRunningMcpCommandsService(
            mcpActivityRepository = activityRepository,
            clock = object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(nowEpochMillis())
            },
        )
        val emissions = mutableListOf<Set<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            service.runningCommandNamesFlow(pluginId = PLUGIN_ID, sessionId = SESSION_ID, commandNames = setOf(TAP_COMMAND_NAME, SWIPE_COMMAND_NAME)).toList(emissions)
        }
        return emissions
    }

    /** Wall-clock time that moves with the test's virtual time. */
    private fun TestScope.nowEpochMillis(): Long = START_EPOCH_MILLIS + testScheduler.currentTime

    private fun invocation(toolName: String, pluginId: String = PLUGIN_ID, sessionId: String = SESSION_ID) = McpToolInvocation(
        id = 1,
        toolName = toolName,
        permission = McpToolPermission.PluginTool(toolName),
        pluginId = pluginId,
        sessionId = sessionId,
        arguments = persistentListOf(),
    )

    private fun finishedCall(toolName: String, finishedAtEpochMillis: Long) = McpCallRecord(
        id = 1,
        toolName = toolName,
        pluginId = PLUGIN_ID,
        sessionId = SESSION_ID,
        succeeded = true,
        finishedAtEpochMillis = finishedAtEpochMillis,
        arguments = persistentListOf(),
        response = "",
    )

    private companion object {
        const val PLUGIN_ID = "com.example.plugin"
        const val SESSION_ID = "session-1"
        const val TAP_COMMAND_NAME = "com.example.plugin.tap"
        const val SWIPE_COMMAND_NAME = "com.example.plugin.swipe"
        const val START_EPOCH_MILLIS = 1_700_000_000_000L
    }
}
