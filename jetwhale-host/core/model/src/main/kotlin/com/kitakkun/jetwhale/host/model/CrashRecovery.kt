package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow
import soil.query.MutationKey
import soil.query.SubscriptionKey

/**
 * What is known about a previous run that ended without a clean shutdown.
 *
 * @property duringStartup The run died before it had been up long enough to count as started, so
 *   something loaded at startup is the likely cause.
 * @property crashLog The JVM's fatal error log for that run, when one was written.
 * @property suspectedPlugin The plugin whose code appears in the crash log's stack, if any.
 * @property logsDirectory Where the host keeps its logs, offered when no crash log was found.
 */
data class UncleanExitReport(
    val pid: Long,
    val startedAtMillis: Long,
    val duringStartup: Boolean,
    val crashLog: JvmCrashLog?,
    val suspectedPlugin: SuspectedPlugin?,
    val logsDirectory: String,
)

data class SuspectedPlugin(
    val pluginId: String,
    val pluginName: String,
)

/**
 * The parts of a HotSpot fatal error log (`hs_err_pid*.log`) worth showing.
 *
 * @property problematicFrame The frame the JVM blames, e.g. `C  [libskiko-macos-arm64.dylib+0x1053d0]  SkBitmap::notifyPixelsChanged() const+0x0`.
 * @property javaFrames The Java frames of the crashing thread, innermost first, as fully qualified method names.
 */
data class JvmCrashLog(
    val path: String,
    val errorLine: String?,
    val problematicFrame: String?,
    val crashingThread: String?,
    val javaFrames: List<String>,
)

/** Detects a previous run's unclean exit and tracks this run so the next one can tell. */
interface CrashRecoveryService {
    val uncleanExitReportFlow: StateFlow<UncleanExitReport?>

    /** Consecutive runs that died during startup, this one excluded. */
    val consecutiveStartupCrashes: Int

    /** Checks for the previous run's marker and leaves one for this run; call once, first thing. */
    fun onStartup()

    fun dismissUncleanExitReport()
}

typealias UncleanExitReportSubscriptionKey = SubscriptionKey<UncleanExitReport?>

interface DismissUncleanExitReportMutationKey : MutationKey<Unit, Unit>
