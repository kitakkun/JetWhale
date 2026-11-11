package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.AI_OPERATION_INDICATOR_LINGER
import com.kitakkun.jetwhale.host.model.McpActivityRepository
import com.kitakkun.jetwhale.host.model.McpCallRecord
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import com.kitakkun.jetwhale.host.model.RunningMcpCommandsService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Clock

/**
 * Reads running commands off [McpActivityRepository]: the calls in flight, and the finished calls it
 * keeps in [com.kitakkun.jetwhale.host.model.McpActivity.recentCalls]. A call that starts and finishes
 * between two samples of the activity is still among the finished ones, so none is missed.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultRunningMcpCommandsService(
    private val mcpActivityRepository: McpActivityRepository,
    private val clock: Clock,
) : RunningMcpCommandsService {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun runningCommandNamesFlow(pluginId: String, sessionId: String, commandNames: Set<String>): Flow<Set<String>> = mcpActivityRepository.activityFlow
        .transformLatest { activity ->
            val runningCommandNames = activity.runningInvocations
                .filter { it.pluginId == pluginId && it.sessionId == sessionId && it.toolName in commandNames }
                .map(McpToolInvocation::toolName)
            val finishedCalls = activity.recentCalls.filter { it.pluginId == pluginId && it.sessionId == sessionId && it.toolName in commandNames }
            while (true) {
                val nowEpochMillis = clock.now().toEpochMilliseconds()
                val lingeringCalls = finishedCalls.filter { nowEpochMillis - it.finishedAtEpochMillis < AI_OPERATION_INDICATOR_LINGER.inWholeMilliseconds }
                emit((runningCommandNames + lingeringCalls.map(McpCallRecord::toolName)).toSet())
                val earliestFinishedAtEpochMillis = lingeringCalls.minOfOrNull(McpCallRecord::finishedAtEpochMillis) ?: break
                delay(earliestFinishedAtEpochMillis + AI_OPERATION_INDICATOR_LINGER.inWholeMilliseconds - nowEpochMillis)
            }
        }
        .distinctUntilChanged()
}
