package com.kitakkun.jetwhale.plugins.mainthread.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationGroup
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** Often enough to watch a stall appear while driving the app, cheap enough to leave running. */
private val REFRESH_INTERVAL = 1.seconds

internal enum class MainThreadTab(val label: String) {
    Hotspots("Hotspots"),
    Timeline("Long tasks & frames"),
    Violations("StrictMode"),
    Settings("Thresholds"),
}

/** Binds the host-owned state — the monitor and the persisted tab — and polls while it is shown. */
@Composable
internal fun MainThreadScreenRoot(monitor: MainThreadMonitor, modifier: Modifier = Modifier) {
    var tab by rememberPersistent("tab", default = MainThreadTab.Hotspots)
    LaunchedEffect(monitor) {
        while (true) {
            delay(REFRESH_INTERVAL)
            // A missed beat is retried on the next one; the toolbar's Refresh reports the error.
            try {
                monitor.load()
            } catch (_: JetWhaleMessagingException) {
            }
        }
    }
    MainThreadScreen(
        tab = tab,
        report = monitor.report,
        status = monitor.status,
        actions = monitor,
        onSelectTab = { tab = it },
        modifier = modifier,
    )
}

@Composable
internal fun MainThreadScreen(
    tab: MainThreadTab,
    report: MainThreadReport?,
    status: MonitorStatus?,
    actions: MainThreadActions,
    onSelectTab: (MainThreadTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            title = "Main thread",
            actions = {
                JwButton(text = "Refresh", onClick = actions::refresh, style = JwButtonStyle.Text)
                JwButton(text = "Clear", onClick = actions::reset, style = JwButtonStyle.Text)
            },
        )
        JwTabRow {
            MainThreadTab.entries.forEach { entry ->
                JwTab(
                    selected = entry == tab,
                    text = entry.label,
                    onClick = { onSelectTab(entry) },
                    count = when (entry) {
                        MainThreadTab.Hotspots -> report?.hotspots?.size
                        MainThreadTab.Timeline -> report?.longTasks?.size
                        MainThreadTab.Violations -> report?.violations?.sumOf(ViolationGroup::count)
                        MainThreadTab.Settings -> null
                    },
                )
            }
        }
        status?.let { JwBanner(text = it.message, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        report?.capabilities?.note?.let { JwBanner(text = it, tone = JwTone.Info) }
        Box(Modifier.weight(1f)) {
            if (report == null) {
                JwEmptyState(title = "Waiting for the app", description = "The report appears once the app's Main Thread Monitor agent answers.")
            } else {
                when (tab) {
                    MainThreadTab.Hotspots -> HotspotsPane(hotspots = report.hotspots, capabilities = report.capabilities, settings = report.settings)

                    MainThreadTab.Timeline -> TimelinePane(report)

                    MainThreadTab.Violations -> ViolationsPane(
                        violations = report.violations,
                        unavailableReason = report.capabilities.takeUnless(MonitorCapabilities::strictMode)?.let {
                            it.note ?: "StrictMode violations are reported on Android 9 (API 28) and later."
                        },
                    )

                    MainThreadTab.Settings -> SettingsPane(report.settings, onApply = actions::updateSettings)
                }
            }
        }
    }
}
