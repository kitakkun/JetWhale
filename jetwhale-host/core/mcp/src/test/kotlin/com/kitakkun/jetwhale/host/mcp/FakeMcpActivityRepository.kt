package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpActivity
import com.kitakkun.jetwhale.host.model.McpActivityRepository
import com.kitakkun.jetwhale.host.model.McpCallArgument
import com.kitakkun.jetwhale.host.model.McpCallRecord
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Mirrors the production repository's bookkeeping, and additionally keeps every invocation it ever
 * saw so tests can assert on calls that have already finished.
 */
class FakeMcpActivityRepository : McpActivityRepository {

    private val nextInvocationId = AtomicLong()

    override val activityFlow: StateFlow<McpActivity>
        field = MutableStateFlow(McpActivity.Idle)

    val recordedInvocations: List<McpToolInvocation> get() = _recordedInvocations.toList()
    private val _recordedInvocations = mutableListOf<McpToolInvocation>()

    override fun clientConnected() {
        activityFlow.update { it.copy(connectedClientCount = it.connectedClientCount + 1) }
    }

    override fun clientDisconnected() {
        activityFlow.update {
            it.copy(connectedClientCount = (it.connectedClientCount - 1).coerceAtLeast(0))
        }
    }

    override fun toolInvocationStarted(
        toolName: String,
        pluginId: String?,
        sessionId: String?,
        arguments: Map<String, String>,
    ): Long {
        val invocation = McpToolInvocation(
            id = nextInvocationId.incrementAndGet(),
            toolName = toolName,
            pluginId = pluginId,
            sessionId = sessionId,
            arguments = arguments
                .map { (name, value) -> McpCallArgument.truncating(name, value) }
                .toImmutableList(),
        )
        _recordedInvocations += invocation
        activityFlow.update {
            it.copy(
                runningInvocations = (it.runningInvocations + invocation).toImmutableList(),
                startedCount = it.startedCount + 1,
                lastStartedInvocation = invocation,
            )
        }
        return invocation.id
    }

    override fun toolInvocationFinished(invocationId: Long, failed: Boolean) {
        val finishedAtEpochMillis = System.currentTimeMillis()
        activityFlow.update { activity ->
            val finished = activity.runningInvocations.firstOrNull { it.id == invocationId }
            activity.copy(
                runningInvocations = activity.runningInvocations
                    .filterNot { it.id == invocationId }
                    .toImmutableList(),
                recentCalls = if (finished == null) {
                    activity.recentCalls
                } else {
                    val record = McpCallRecord(
                        id = finished.id,
                        toolName = finished.toolName,
                        pluginId = finished.pluginId,
                        sessionId = finished.sessionId,
                        succeeded = !failed,
                        finishedAtEpochMillis = finishedAtEpochMillis,
                        arguments = finished.arguments,
                    )
                    (listOf(record) + activity.recentCalls)
                        .take(McpActivity.MAX_RECENT_CALLS)
                        .toImmutableList()
                },
            )
        }
    }

    override fun clear() {
        activityFlow.value = McpActivity.Idle
    }
}
