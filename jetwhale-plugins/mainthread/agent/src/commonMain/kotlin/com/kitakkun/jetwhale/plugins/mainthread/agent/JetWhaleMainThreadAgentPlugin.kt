package com.kitakkun.jetwhale.plugins.mainthread.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.mainthread.protocol.GetMainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MAIN_THREAD_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ResetMainThreadStats
import com.kitakkun.jetwhale.plugins.mainthread.protocol.UpdateMonitorSettings
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply

/**
 * Names what the main thread is running right now in the app's own terms — the coroutine being
 * resumed, the screen being drawn. Whatever it returns when a long task starts is added to that
 * task's label. Returning null adds nothing.
 */
fun interface MainThreadLabelProvider {
    fun currentLabel(): String?
}

/**
 * Agent plugin that finds what blocks the main thread: tasks that run long and where their stacks
 * were while they did, StrictMode's disk and network violations, and janky frames. It needs no
 * code in the app beyond registering it:
 *
 * ```kotlin
 * startJetWhale { plugins { register(JetWhaleMainThreadAgentPlugin()) } }
 * ```
 *
 * It hooks the main thread only while the host has the plugin enabled, and puts back whatever the
 * app had in place when it is disabled.
 */
class JetWhaleMainThreadAgentPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String get() = MAIN_THREAD_PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    /** Adds the app's own name for the running work to each long task; see [MainThreadLabelProvider]. */
    var labelProvider: MainThreadLabelProvider? = null

    private val recorder = MainThreadRecorder(
        clock = SystemMonitorClock(),
        initialSettings = MonitorSettings(
            longTaskThresholdMillis = DEFAULT_LONG_TASK_MILLIS,
            sampleIntervalMillis = DEFAULT_SAMPLE_INTERVAL_MILLIS,
            unresponsiveThresholdMillis = DEFAULT_UNRESPONSIVE_MILLIS,
        ),
    )

    private val probe: MainThreadProbe by lazy { createMainThreadProbe(recorder) { labelProvider?.currentLabel() } }

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { _: GetMainThreadReport -> reply(recorder.report(probe.capabilities)) }
        onRequest { request: UpdateMonitorSettings -> reply(recorder.updateSettings(request.settings)) }
        onRequest { _: ResetMainThreadStats ->
            recorder.reset()
            reply(recorder.report(probe.capabilities))
        }
    }

    override fun onActivate() {
        recorder.reset()
        probe.start()
    }

    override fun onDeactivate() {
        probe.stop()
    }
}

/** Long enough to skip ordinary layout and input handling, short enough to catch visible stutter. */
private const val DEFAULT_LONG_TASK_MILLIS = 100L

/** Fine enough to place a 100 ms stall within a few frames; reading a stack costs well under 1 ms. */
private const val DEFAULT_SAMPLE_INTERVAL_MILLIS = 20L

/** Android reports an ANR when input goes unanswered for 5 seconds. */
private const val DEFAULT_UNRESPONSIVE_MILLIS = 5_000L
