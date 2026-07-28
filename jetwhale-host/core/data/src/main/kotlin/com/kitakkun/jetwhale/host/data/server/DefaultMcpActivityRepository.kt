package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.McpActivity
import com.kitakkun.jetwhale.host.model.McpActivityRepository
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultMcpActivityRepository : McpActivityRepository {

    private val nextInvocationId = AtomicLong()

    override val activityFlow: StateFlow<McpActivity>
        field = MutableStateFlow(McpActivity.Idle)

    override fun clientConnected() {
        activityFlow.update { it.copy(connectedClientCount = it.connectedClientCount + 1) }
    }

    override fun clientDisconnected() {
        activityFlow.update {
            it.copy(connectedClientCount = (it.connectedClientCount - 1).coerceAtLeast(0))
        }
    }

    override fun toolInvocationStarted(toolName: String, pluginId: String?, sessionId: String?): Long {
        val invocationId = nextInvocationId.incrementAndGet()
        val invocation = McpToolInvocation(
            id = invocationId,
            toolName = toolName,
            pluginId = pluginId,
            sessionId = sessionId,
        )
        activityFlow.update {
            it.copy(
                runningInvocations = (it.runningInvocations + invocation).toImmutableList(),
                startedCount = it.startedCount + 1,
                lastStartedInvocation = invocation,
            )
        }
        return invocationId
    }

    override fun toolInvocationFinished(invocationId: Long) {
        activityFlow.update { activity ->
            activity.copy(
                runningInvocations = activity.runningInvocations
                    .filterNot { it.id == invocationId }
                    .toImmutableList(),
            )
        }
    }

    override fun clear() {
        activityFlow.value = McpActivity.Idle
    }
}
