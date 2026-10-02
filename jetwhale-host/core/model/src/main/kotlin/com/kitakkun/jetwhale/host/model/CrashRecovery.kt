package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow
import soil.query.MutationKey
import soil.query.SubscriptionKey

/**
 * What is known about a previous run that ended without a clean shutdown.
 *
 * @property crashLog The JVM's fatal error log for that run, when one was written.
 * @property suspectedPlugin The plugin whose code appears in the crash log's stack, if any.
 * @property logsDirectory Where the host keeps its own logs.
 */
data class UncleanExitReport(
    val crashLog: JvmCrashLog?,
    val suspectedPlugin: SuspectedPlugin?,
    val logsDirectory: String,
)

data class SuspectedPlugin(
    val pluginId: String,
    val pluginName: String,
)

/**
 * A HotSpot fatal error log (`hs_err_pid*.log`).
 *
 * @property javaFrames The Java frames of the crashing thread, innermost first, as fully qualified method names.
 */
data class JvmCrashLog(
    val path: String,
    val javaFrames: List<String>,
)

/** Detects a previous run's unclean exit and tracks this run so the next one can tell. */
interface CrashRecoveryService {
    val uncleanExitReportFlow: StateFlow<UncleanExitReport?>

    /** Consecutive runs that died during startup, this one excluded. */
    val consecutiveStartupCrashes: Int

    /** Checks for markers left by runs that died and leaves one for this run; call once, first thing. */
    fun onStartup()

    /** Removes this run's marker for good; runs from a JVM shutdown hook that [onStartup] registers. */
    fun onCleanShutdown()

    fun dismissUncleanExitReport()
}

typealias UncleanExitReportSubscriptionKey = SubscriptionKey<UncleanExitReport?>

interface DismissUncleanExitReportMutationKey : MutationKey<Unit, Unit>
