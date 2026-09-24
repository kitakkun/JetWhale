package com.kitakkun.jetwhale.plugins.mainthread.host

import com.kitakkun.jetwhale.plugins.mainthread.protocol.FrameStats
import com.kitakkun.jetwhale.plugins.mainthread.protocol.Hotspot
import com.kitakkun.jetwhale.plugins.mainthread.protocol.LongTask
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationGroup
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationKind

/** An app whose agent answers with [current], and forgets it on reset. */
internal class FakeMainThreadClient(private var current: MainThreadReport) : MainThreadClient {
    var resets = 0
        private set

    override suspend fun report(): MainThreadReport = current

    override suspend fun updateSettings(settings: MonitorSettings): MonitorSettings {
        current = current.copy(settings = settings)
        return settings
    }

    override suspend fun reset(): MainThreadReport {
        resets++
        current = current.copy(hotspots = emptyList(), longTasks = emptyList(), violations = emptyList())
        return current
    }
}

internal fun report(hotspots: List<Hotspot>, violations: List<ViolationGroup>, longTasks: List<LongTask>): MainThreadReport = MainThreadReport(
    capabilities = MonitorCapabilities(platform = "test", taskTiming = true, stackSampling = true, strictMode = true, frameTiming = true, note = null),
    settings = MonitorSettings(longTaskThresholdMillis = 100, sampleIntervalMillis = 20, unresponsiveThresholdMillis = 5_000),
    recordingSinceEpochMillis = 1_700_000_000_000,
    longTasks = longTasks,
    hotspots = hotspots,
    violations = violations,
    frames = FrameStats(totalFrames = 0, jankyFrames = 0, refreshIntervalMillis = 16.67, p50Millis = 0.0, p90Millis = 0.0, p99Millis = 0.0, slowestMillis = 0.0, recentJankyFrames = emptyList()),
)

internal fun hotspot(signature: String, samples: Int): Hotspot = Hotspot(signature = signature, frames = listOf(signature), sampleCount = samples, blockedMillis = samples * 20L, taskCount = 1)

internal fun violation(kind: ViolationKind, callSite: String): ViolationGroup = ViolationGroup(kind = kind, callSite = callSite, message = kind.name, stack = listOf(callSite), count = 1, lastEpochMillis = 1_700_000_000_000)

internal fun longTask(duration: Long): LongTask = LongTask(startEpochMillis = 1_700_000_000_000, durationMillis = duration, label = "task", sampleCount = 0, unresponsive = duration >= 5_000)
