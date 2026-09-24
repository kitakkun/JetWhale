package com.kitakkun.jetwhale.plugins.coroutines.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The pluginId shared by the Coroutine Inspector agent and host plugins. */
const val COROUTINES_PLUGIN_ID: String = "com.kitakkun.jetwhale.coroutines"

/**
 * Asks for the coroutines below every scope the app registered, as they stand now. The agent
 * pushes nothing on its own: the host asks while someone is looking, so an idle inspector costs
 * the app nothing.
 */
@SerialName("coroutines/get_tree")
@Serializable
data object GetCoroutineTree : JetWhaleRequest<CoroutineTree>

@SerialName("coroutines/get_dispatcher_stats")
@Serializable
data object GetDispatcherStats : JetWhaleRequest<DispatcherStatsReport>

@SerialName("coroutines/get_flows")
@Serializable
data object GetTrackedFlows : JetWhaleRequest<TrackedFlowReport>

/**
 * Asks for a dump of every coroutine with its suspension stack. Only an app on the JVM that has
 * installed `kotlinx-coroutines-debug`'s DebugProbes can answer it; elsewhere the reply says why not.
 */
@SerialName("coroutines/dump")
@Serializable
data object DumpCoroutines : JetWhaleRequest<CoroutineDump>

/** Forgets the long runs recorded so far, so the next report shows only what happens from now on. */
@SerialName("coroutines/clear_long_runs")
@Serializable
data object ClearLongRuns : JetWhaleRequest<ClearedLongRuns>

@SerialName("coroutines/cleared_long_runs")
@Serializable
data class ClearedLongRuns(val cleared: Int)
