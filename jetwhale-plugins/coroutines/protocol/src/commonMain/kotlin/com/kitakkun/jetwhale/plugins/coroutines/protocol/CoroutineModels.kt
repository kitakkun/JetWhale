package com.kitakkun.jetwhale.plugins.coroutines.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The state of a coroutine as its `Job` reports it.
 *
 * `Job`'s public API cannot tell a coroutine whose body is running from one whose body has
 * finished and is waiting for its children ("completing"): both read as [Active].
 */
@Serializable
enum class CoroutineState {
    /** Created lazily and not started yet. */
    New,
    Active,
    Cancelling,
    Completed,
    Cancelled,
}

/**
 * The coroutines below the registered scopes.
 *
 * @property truncated True when the tree had more coroutines than the agent walks in one reply;
 *   [coroutineCount] still counts only the ones included.
 */
@SerialName("coroutines/tree")
@Serializable
data class CoroutineTree(
    val roots: List<CoroutineNode>,
    val coroutineCount: Int,
    val truncated: Boolean,
    val capturedAtEpochMillis: Long,
)

/**
 * One coroutine, or one registered scope at the top of the tree.
 *
 * @property id Stable for as long as the agent keeps seeing this coroutine.
 * @property name The coroutine's `CoroutineName`, or the name a scope was registered under.
 * @property observedMillis How long the agent has seen this coroutine: counted from the first time
 *   a walk found it, since a `Job` does not record when it started.
 * @property dispatcher The coroutine's dispatcher as it describes itself, when it has one of its own.
 * @property description The `Job`'s own `toString()`, which names its implementation class.
 */
@Serializable
data class CoroutineNode(
    val id: String,
    val name: String?,
    val state: CoroutineState,
    val observedMillis: Long,
    val dispatcher: String?,
    val description: String,
    val children: List<CoroutineNode>,
)

@SerialName("coroutines/dispatcher_stats")
@Serializable
data class DispatcherStatsReport(
    val dispatchers: List<DispatcherStats>,
    val capturedAtEpochMillis: Long,
)

/**
 * What a tracked dispatcher has done since the app started tracking it.
 *
 * @property queued Tasks dispatched and not started yet.
 * @property running Tasks started and not finished yet.
 * @property longRunThresholdMillis A task that runs this long or longer is recorded in [longRuns].
 * @property longRuns The most recent long runs, newest first.
 */
@Serializable
data class DispatcherStats(
    val name: String,
    val queued: Int,
    val running: Int,
    val completedTasks: Long,
    val averageQueueLatencyMillis: Double,
    val maxQueueLatencyMillis: Double,
    val averageRunMillis: Double,
    val maxRunMillis: Double,
    val longRunThresholdMillis: Long,
    val longRuns: List<LongRun>,
)

/** A task that held a tracked dispatcher's thread for at least its long-run threshold. */
@Serializable
data class LongRun(
    val atEpochMillis: Long,
    val durationMillis: Double,
    val coroutineName: String?,
)

@SerialName("coroutines/flows")
@Serializable
data class TrackedFlowReport(
    val flows: List<TrackedFlowInfo>,
    val capturedAtEpochMillis: Long,
)

/**
 * What one tracked flow has done since the app started tracking it.
 *
 * @property activeCollectors Collections running right now.
 * @property emissionsPerSecond Emissions over the last ten seconds, per second.
 * @property recentValues The last values emitted, newest first, as text cut to a fixed length.
 */
@Serializable
data class TrackedFlowInfo(
    val name: String,
    val activeCollectors: Int,
    val collections: Long,
    val completions: Long,
    val cancellations: Long,
    val failures: Long,
    val emissions: Long,
    val emissionsPerSecond: Double,
    val recentValues: List<FlowValue>,
)

@Serializable
data class FlowValue(
    val atEpochMillis: Long,
    val text: String,
)

/**
 * A full dump of the app's coroutines.
 *
 * @property text The dump, with a suspension stack per coroutine; empty when [unavailableReason] is set.
 * @property unavailableReason Why this app cannot produce a dump, or null when it did.
 */
@SerialName("coroutines/dump_result")
@Serializable
data class CoroutineDump(
    val text: String,
    val unavailableReason: String?,
)

/**
 * What the agent can tell about one coroutine beyond the tree.
 *
 * @property found False when the agent no longer knows [id]: the coroutine finished, or its scope
 *   was dropped, since the tree was read.
 * @property debugState DebugProbes' view of it — `CREATED`, `RUNNING` or `SUSPENDED` — which tells
 *   apart what [CoroutineState.Active] cannot; null when there are no stacks.
 * @property suspensionStack The frames it was last seen at, innermost first: where it is suspended,
 *   or where it was when it last suspended if it is running now.
 * @property creationStack The frames that created it, innermost first; empty when DebugProbes do
 *   not record creation stacks.
 * @property stackUnavailableReason Why there are no stacks, or null when there are.
 */
@SerialName("coroutines/detail")
@Serializable
data class CoroutineDetail(
    val id: String,
    val found: Boolean,
    val debugState: String?,
    val suspensionStack: List<String>,
    val creationStack: List<String>,
    val stackUnavailableReason: String?,
)
