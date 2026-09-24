package com.kitakkun.jetwhale.plugins.mainthread.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.mainthread.protocol.FrameStats
import com.kitakkun.jetwhale.plugins.mainthread.protocol.Hotspot
import com.kitakkun.jetwhale.plugins.mainthread.protocol.JankyFrame
import com.kitakkun.jetwhale.plugins.mainthread.protocol.LongTask
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationGroup
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationKind

private const val PREVIEW_NOW = 1_760_000_060_000L

private val previewSettings = MonitorSettings(longTaskThresholdMillis = 100, sampleIntervalMillis = 20, unresponsiveThresholdMillis = 5_000)

private val previewCapabilities = MonitorCapabilities(
    platform = "Android 15 (API 35)",
    taskTiming = true,
    stackSampling = true,
    strictMode = true,
    frameTiming = true,
    note = null,
)

private val previewStack = listOf(
    "java.io.FileOutputStream.write(FileOutputStream.java:354)",
    "android.app.SharedPreferencesImpl\$EditorImpl.commit(SharedPreferencesImpl.java:604)",
    "com.example.settings.SettingsStore.save(SettingsStore.kt:42)",
    "com.example.settings.SettingsViewModel.onToggle(SettingsViewModel.kt:18)",
    "android.os.Looper.loop(Looper.java:288)",
)

private val previewReport = MainThreadReport(
    capabilities = previewCapabilities,
    settings = previewSettings,
    recordingSinceEpochMillis = PREVIEW_NOW - 60_000,
    longTasks = listOf(
        LongTask(startEpochMillis = PREVIEW_NOW - 40_000, durationMillis = 180, label = "Handler (android.view.Choreographer\$FrameHandler) android.view.Choreographer\$FrameDisplayEventReceiver", sampleCount = 4, unresponsive = false),
        LongTask(startEpochMillis = PREVIEW_NOW - 12_000, durationMillis = 5_400, label = "Handler (android.os.Handler) com.example.settings.SettingsViewModel\$save\$1", sampleCount = 265, unresponsive = true),
    ),
    hotspots = listOf(
        Hotspot(signature = "com.example.settings.SettingsStore.save(SettingsStore.kt:42) ← com.example.settings.SettingsViewModel.onToggle(SettingsViewModel.kt:18)", frames = previewStack, sampleCount = 265, blockedMillis = 5_300, taskCount = 3),
        Hotspot(signature = "com.example.feed.ImageDecoder.decode(ImageDecoder.kt:77)", frames = listOf("com.example.feed.ImageDecoder.decode(ImageDecoder.kt:77)"), sampleCount = 4, blockedMillis = 80, taskCount = 1),
    ),
    violations = listOf(
        ViolationGroup(kind = ViolationKind.DiskWrite, callSite = "com.example.settings.SettingsStore.save(SettingsStore.kt:42)", message = "DiskWriteViolation", stack = previewStack, count = 3, lastEpochMillis = PREVIEW_NOW - 12_000),
    ),
    frames = FrameStats(
        totalFrames = 3_600,
        jankyFrames = 14,
        refreshIntervalMillis = 16.67,
        p50Millis = 8.2,
        p90Millis = 14.9,
        p99Millis = 41.0,
        slowestMillis = 5_410.0,
        recentJankyFrames = listOf(JankyFrame(endEpochMillis = PREVIEW_NOW - 39_800, durationMillis = 190.0), JankyFrame(endEpochMillis = PREVIEW_NOW - 6_600, durationMillis = 5_410.0)),
    ),
)

private object NoActions : MainThreadActions {
    override fun refresh() = Unit

    override fun reset() = Unit

    override fun updateSettings(settings: MonitorSettings) = Unit
}

@Preview
@Composable
private fun MainThreadScreenHotspotsPreview() {
    JwTheme(darkTheme = false) {
        MainThreadScreen(tab = MainThreadTab.Hotspots, report = previewReport, status = null, actions = NoActions, onSelectTab = {})
    }
}

@Preview
@Composable
private fun MainThreadScreenUnsupportedPreview() {
    JwTheme(darkTheme = true) {
        MainThreadScreen(
            tab = MainThreadTab.Hotspots,
            report = previewReport.copy(
                capabilities = MonitorCapabilities(platform = "Apple", taskTiming = false, stackSampling = false, strictMode = false, frameTiming = false, note = "Main-thread monitoring is not available on iOS or macOS yet."),
                hotspots = emptyList(),
                longTasks = emptyList(),
                violations = emptyList(),
            ),
            status = null,
            actions = NoActions,
            onSelectTab = {},
        )
    }
}

@Preview
@Composable
private fun HotspotsPanePreview() {
    JwTheme(darkTheme = false) {
        HotspotsPane(hotspots = previewReport.hotspots, capabilities = previewCapabilities, settings = previewSettings)
    }
}

@Preview
@Composable
private fun TimelinePanePreview() {
    JwTheme(darkTheme = true) {
        TimelinePane(previewReport)
    }
}

@Preview
@Composable
private fun ViolationsPanePreview() {
    JwTheme(darkTheme = false) {
        ViolationsPane(violations = previewReport.violations, unavailableReason = null)
    }
}

@Preview
@Composable
private fun SettingsPanePreview() {
    JwTheme(darkTheme = false) {
        SettingsPane(settings = previewSettings, onApply = {})
    }
}
