package com.kitakkun.jetwhale.plugins.mainthread.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.mainthread.protocol.GetMainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MAIN_THREAD_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ResetMainThreadStats
import com.kitakkun.jetwhale.plugins.mainthread.protocol.UpdateMonitorSettings
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import kotlin.concurrent.Volatile

/**
 * Names the app's state as a main-thread task starts — the screen shown, the feature in use. It is
 * asked on the main thread before each task runs, for every task, so it must be cheap, and it sees
 * the state the task started in, not what the task goes on to do. Whatever it returns is added to
 * the label of a task that turns out long; returning null adds nothing.
 */
fun interface MainThreadLabelProvider {
    fun currentLabel(): String?
}

/**
 * Agent plugin that finds what blocks the main thread: tasks that run long and where their stacks
 * were while they did, StrictMode's disk and slow-call violations, and janky frames. It needs no
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

    /** Adds the app's state when each long task started to its label; see [MainThreadLabelProvider]. */
    var labelProvider: MainThreadLabelProvider? = null

    private val recorder = MainThreadRecorder(
        clock = SystemMonitorClock(),
        initialSettings = MonitorSettings(
            longTaskThresholdMillis = DEFAULT_LONG_TASK_MILLIS,
            sampleIntervalMillis = DEFAULT_SAMPLE_INTERVAL_MILLIS,
            unresponsiveThresholdMillis = DEFAULT_UNRESPONSIVE_MILLIS,
        ),
    )

    @Volatile private var hostConnected = false

    private val probe: MainThreadProbe by lazy {
        createMainThreadProbe(recorder, labels = { labelProvider?.currentLabel() }, hostConnected = { hostConnected })
    }

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

    override suspend fun onPrepare() {
        hostConnected = true
    }

    override suspend fun onDisconnected() {
        hostConnected = false
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
