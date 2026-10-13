package com.kitakkun.jetwhale.plugins.mainthread.protocol

import kotlinx.serialization.Serializable

/**
 * What the agent records.
 *
 * @property longTaskThresholdMillis A main-thread task at least this long is recorded, and its
 *   stack is sampled for as long as it keeps running.
 * @property sampleIntervalMillis How often the stack of a long task is sampled.
 * @property unresponsiveThresholdMillis A task still running after this long is also recorded as
 *   the app being unresponsive, the way an ANR would report it.
 */
@Serializable
data class MonitorSettings(
    val longTaskThresholdMillis: Long,
    val sampleIntervalMillis: Long,
    val unresponsiveThresholdMillis: Long,
)

/**
 * What the platform the app runs on lets the agent see. Anything false is simply never recorded;
 * [note] says why, for the host to show.
 */
@Serializable
data class MonitorCapabilities(
    val platform: String,
    val taskTiming: Boolean,
    val stackSampling: Boolean,
    val strictMode: Boolean,
    val frameTiming: Boolean,
    val note: String?,
)

/**
 * One main-thread task that ran at least the long-task threshold.
 *
 * @property label What was dispatched: the Handler and callback on Android, the event on the JVM.
 * @property sampleCount How many stack samples were taken while it ran; they feed [Hotspot]s.
 * @property unresponsive Whether it ran past the unresponsive threshold.
 */
@Serializable
data class LongTask(
    val startEpochMillis: Long,
    val durationMillis: Long,
    val label: String,
    val sampleCount: Int,
    val unresponsive: Boolean,
)

/**
 * Where long tasks spent their time: stack samples that share a [signature] (the innermost frames
 * that belong to the app rather than the platform), ranked by the time they account for.
 *
 * @property frames The full stack of the most recent sample, innermost frame first.
 * @property blockedMillis Samples × the sample interval: an estimate of the time the main thread
 *   spent there.
 */
@Serializable
data class Hotspot(
    val signature: String,
    val frames: List<String>,
    val sampleCount: Int,
    val blockedMillis: Long,
    val taskCount: Int,
)

/** The kinds of work StrictMode reports on the main thread. */
@Serializable
enum class ViolationKind {
    DiskRead,
    DiskWrite,
    Network,
    CustomSlowCall,
    ResourceMismatch,
    UnbufferedIo,
    Other,
}

/**
 * StrictMode violations of one [kind] from one call site.
 *
 * @property callSite The innermost frame of the app that led to the violation.
 * @property stack The stack of the most recent occurrence, innermost frame first.
 */
@Serializable
data class ViolationGroup(
    val kind: ViolationKind,
    val callSite: String,
    val message: String,
    val stack: List<String>,
    val count: Int,
    val lastEpochMillis: Long,
)

/**
 * Frame durations the platform reported.
 *
 * @property jankyFrames Frames longer than one and a half refresh intervals.
 * @property recentJankyFrames The latest janky frames, for placing on a timeline.
 */
@Serializable
data class FrameStats(
    val totalFrames: Int,
    val jankyFrames: Int,
    val refreshIntervalMillis: Double,
    val p50Millis: Double,
    val p90Millis: Double,
    val p99Millis: Double,
    val slowestMillis: Double,
    val recentJankyFrames: List<JankyFrame>,
)

@Serializable
data class JankyFrame(
    val endEpochMillis: Long,
    val durationMillis: Double,
)

/** Everything the agent has recorded since it was activated or last reset. */
@Serializable
data class MainThreadReport(
    val capabilities: MonitorCapabilities,
    val settings: MonitorSettings,
    val recordingSinceEpochMillis: Long,
    val longTasks: List<LongTask>,
    val hotspots: List<Hotspot>,
    val violations: List<ViolationGroup>,
    val frames: FrameStats,
)
