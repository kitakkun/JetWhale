package com.kitakkun.jetwhale.plugins.background.protocol

import kotlinx.serialization.Serializable

/** Where a piece of background work stands, mapped from each scheduler's own states. */
@Serializable
enum class WorkState {
    /** Waiting for its constraints or its time to come (WorkManager ENQUEUED). */
    Enqueued,

    /** Waiting for work it depends on to finish (WorkManager BLOCKED). */
    Blocked,

    /** Handed to the system to run later: a pending JobScheduler job, an alarm, a BGTask request. */
    Scheduled,

    Running,
    Succeeded,
    Failed,
    Cancelled,
}

/**
 * One piece of background work as the agent saw it.
 *
 * @property source The name of the [WorkSourceInfo] that reported it.
 * @property id Identifies the work within its source: a WorkManager UUID, a job id, a BGTask identifier.
 * @property name What runs: the worker or job service class, or the task identifier.
 * @property uniqueName The unique work name, when the source can tell.
 * @property runAttemptCount How many times it has been attempted, when the source counts.
 * @property constraints Human-readable conditions it waits for, such as "network: UNMETERED".
 * @property nextRunEpochMillis The earliest time it may run next, when the source knows.
 * @property periodMillis The repeat interval of periodic work; null for one-off work.
 * @property flexMillis The flex window of periodic work.
 * @property progress The progress data a running worker has published.
 * @property output The output data a finished worker returned.
 * @property stopReason Why the system last stopped it, when it was stopped.
 * @property details Anything else the source reports, as label to value.
 * @property canCancel Whether [CancelWork] by id works for it.
 * @property canRunNow Whether [RunWorkNow] works for it.
 * @property runNowHint How to force a run from outside the app when the app itself cannot, such as
 *   an adb or lldb command.
 */
@Serializable
data class BackgroundWorkItem(
    val source: String,
    val id: String,
    val name: String,
    val state: WorkState,
    val tags: List<String>,
    val uniqueName: String?,
    val runAttemptCount: Int?,
    val constraints: List<String>,
    val nextRunEpochMillis: Long?,
    val periodMillis: Long?,
    val flexMillis: Long?,
    val progress: Map<String, String>,
    val output: Map<String, String>,
    val stopReason: String?,
    val details: Map<String, String>,
    val canCancel: Boolean,
    val canRunNow: Boolean,
    val runNowHint: String?,
)

/**
 * A scheduler the agent reads.
 *
 * @property available False when the scheduler is not usable here; [unavailableReason] says why.
 * @property supportsCancelByTag Whether [CancelTarget.ByTag] works for this source.
 * @property supportsCancelByUniqueName Whether [CancelTarget.ByUniqueName] works for this source.
 */
@Serializable
data class WorkSourceInfo(
    val name: String,
    val available: Boolean,
    val unavailableReason: String?,
    val supportsCancelByTag: Boolean,
    val supportsCancelByUniqueName: Boolean,
)

/** Everything the agent reports at one moment. */
@Serializable
data class BackgroundWorkSnapshot(
    val sources: List<WorkSourceInfo>,
    val items: List<BackgroundWorkItem>,
)

/** What [CancelWork] cancels within one source. */
@Serializable
sealed interface CancelTarget {
    @Serializable
    data class ById(val id: String) : CancelTarget

    @Serializable
    data class ByTag(val tag: String) : CancelTarget

    @Serializable
    data class ByUniqueName(val uniqueName: String) : CancelTarget
}
