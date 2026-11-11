package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.AI_OPERATION_INDICATOR_LINGER
import com.kitakkun.jetwhale.host.model.AiOperatedUiTargetsService
import com.kitakkun.jetwhale.host.sdk.AiOperatedUiTargetsReporter
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

/**
 * Counts the running operations of each UI target per plugin instance, and remembers when the last
 * one ended. A target is operated from its first start until [AI_OPERATION_INDICATOR_LINGER] after
 * its last end, so overlapping operations of one target read as one.
 *
 * Reports arrive from plugin coroutines on any thread; every change is a compare-and-set of the
 * whole state.
 */
@OptIn(InternalJetWhaleHostApi::class)
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAiOperatedUiTargetsService(
    private val clock: Clock,
) : AiOperatedUiTargetsService {
    private val operationsByPluginInstance = MutableStateFlow<Map<PluginInstanceKey, Map<Any, UiTargetOperations>>>(emptyMap())

    override fun reporterFor(pluginId: String, sessionId: String): AiOperatedUiTargetsReporter = PluginInstanceReporter(PluginInstanceKey(pluginId, sessionId))

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun operatedUiTargetsFlow(pluginId: String, sessionId: String): Flow<Set<Any>> {
        val pluginInstanceKey = PluginInstanceKey(pluginId, sessionId)
        return operationsByPluginInstance
            .map { it[pluginInstanceKey].orEmpty() }
            .distinctUntilChanged()
            .transformLatest { operationsByTarget ->
                while (true) {
                    val nowEpochMillis = clock.now().toEpochMilliseconds()
                    emit(operationsByTarget.filterValues { isTargetOperated(it, nowEpochMillis) }.keys)
                    val nextHoldEndEpochMillis = operationsByTarget.values
                        .filter { it.runningCount == 0 }
                        .mapNotNull { it.lastEndedAtEpochMillis?.plus(AI_OPERATION_INDICATOR_LINGER.inWholeMilliseconds) }
                        .filter { it > nowEpochMillis }
                        .minOrNull()
                        ?: break
                    delay(nextHoldEndEpochMillis - nowEpochMillis)
                }
            }
            .distinctUntilChanged()
    }

    private inner class PluginInstanceReporter(private val pluginInstanceKey: PluginInstanceKey) : AiOperatedUiTargetsReporter() {
        override fun reportOperationStarted(targets: Set<Any>) {
            recordOperationsStarted(pluginInstanceKey, targets)
        }

        override fun reportOperationEnded(targets: Set<Any>) {
            recordOperationsEnded(pluginInstanceKey, targets)
        }
    }

    private fun recordOperationsStarted(pluginInstanceKey: PluginInstanceKey, targets: Set<Any>) {
        operationsByPluginInstance.update { all ->
            val operationsByTarget = all[pluginInstanceKey].orEmpty().toMutableMap()
            targets.forEach { target ->
                val operations = operationsByTarget[target]
                operationsByTarget[target] = UiTargetOperations(runningCount = (operations?.runningCount ?: 0) + 1, lastEndedAtEpochMillis = operations?.lastEndedAtEpochMillis)
            }
            withoutEndedHolds(all + (pluginInstanceKey to operationsByTarget), clock.now().toEpochMilliseconds())
        }
    }

    private fun recordOperationsEnded(pluginInstanceKey: PluginInstanceKey, targets: Set<Any>) {
        operationsByPluginInstance.update { all ->
            val nowEpochMillis = clock.now().toEpochMilliseconds()
            val operationsByTarget = all[pluginInstanceKey].orEmpty().toMutableMap()
            targets.forEach { target ->
                val operations = operationsByTarget.getValue(target)
                operationsByTarget[target] = UiTargetOperations(runningCount = operations.runningCount - 1, lastEndedAtEpochMillis = nowEpochMillis)
            }
            withoutEndedHolds(all + (pluginInstanceKey to operationsByTarget), nowEpochMillis)
        }
    }

    /** Drops the targets no longer operated, so the state holds only what some UI may still show. */
    private fun withoutEndedHolds(all: Map<PluginInstanceKey, Map<Any, UiTargetOperations>>, nowEpochMillis: Long): Map<PluginInstanceKey, Map<Any, UiTargetOperations>> = all
        .mapValues { (_, operationsByTarget) -> operationsByTarget.filterValues { isTargetOperated(it, nowEpochMillis) } }
        .filterValues { it.isNotEmpty() }

    private fun isTargetOperated(operations: UiTargetOperations, nowEpochMillis: Long): Boolean = operations.runningCount > 0 ||
        operations.lastEndedAtEpochMillis.let { it != null && nowEpochMillis < it + AI_OPERATION_INDICATOR_LINGER.inWholeMilliseconds }

}

private data class PluginInstanceKey(val pluginId: String, val sessionId: String)

/** [runningCount] operations of one target are running; the last one to end ended at [lastEndedAtEpochMillis]. */
private data class UiTargetOperations(val runningCount: Int, val lastEndedAtEpochMillis: Long?)
