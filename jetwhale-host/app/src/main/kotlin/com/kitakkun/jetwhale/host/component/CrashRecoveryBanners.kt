package com.kitakkun.jetwhale.host.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.close
import com.kitakkun.jetwhale.host.model.JvmCrashLog
import com.kitakkun.jetwhale.host.model.SafeMode
import com.kitakkun.jetwhale.host.model.SafeModeReason
import com.kitakkun.jetwhale.host.model.SuspectedPlugin
import com.kitakkun.jetwhale.host.model.UncleanExitReport
import com.kitakkun.jetwhale.host.safe_mode_banner_load_plugins
import com.kitakkun.jetwhale.host.safe_mode_banner_requested
import com.kitakkun.jetwhale.host.safe_mode_banner_startup_crashes
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.unclean_exit_banner_disable_plugin
import com.kitakkun.jetwhale.host.unclean_exit_banner_message
import com.kitakkun.jetwhale.host.unclean_exit_banner_message_with_plugin
import com.kitakkun.jetwhale.host.unclean_exit_banner_open_crash_log
import com.kitakkun.jetwhale.host.unclean_exit_banner_open_logs
import org.jetbrains.compose.resources.stringResource

/**
 * Shown once after a run that ended without shutting down: a native crash, a kill, a power loss.
 * Names the plugin whose code was on the crashing thread when the JVM left a crash log that says so.
 */
@Composable
fun UncleanExitBanner(
    report: UncleanExitReport,
    onClickOpenCrashLog: (path: String) -> Unit,
    onClickOpenLogs: (directory: String) -> Unit,
    onClickDisablePlugin: (pluginId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suspect = report.suspectedPlugin
    JwBanner(
        text = if (suspect == null) {
            stringResource(Res.string.unclean_exit_banner_message)
        } else {
            stringResource(Res.string.unclean_exit_banner_message_with_plugin, suspect.pluginName)
        },
        modifier = modifier,
        tone = JwTone.Error,
        actions = {
            report.crashLog?.let { crashLog ->
                JwButton(
                    text = stringResource(Res.string.unclean_exit_banner_open_crash_log),
                    onClick = { onClickOpenCrashLog(crashLog.path) },
                    style = JwButtonStyle.Text,
                )
            }
            JwButton(
                text = stringResource(Res.string.unclean_exit_banner_open_logs),
                onClick = { onClickOpenLogs(report.logsDirectory) },
                style = JwButtonStyle.Text,
            )
            suspect?.let {
                JwButton(
                    text = stringResource(Res.string.unclean_exit_banner_disable_plugin, suspect.pluginName),
                    onClick = { onClickDisablePlugin(suspect.pluginId) },
                    style = JwButtonStyle.Text,
                )
            }
        },
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.close),
    )
}

/**
 * Shown for as long as the host runs without plugins, whether the command line asked for it or
 * repeated crashes during startup did.
 */
@Composable
fun SafeModeBanner(
    safeMode: SafeMode,
    onClickLoadPlugins: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwBanner(
        text = when (safeMode.reason) {
            SafeModeReason.RequestedOnCommandLine -> stringResource(Res.string.safe_mode_banner_requested)
            SafeModeReason.RepeatedStartupCrashes -> stringResource(Res.string.safe_mode_banner_startup_crashes)
        },
        modifier = modifier,
        tone = JwTone.Warning,
        actions = {
            JwButton(
                text = stringResource(Res.string.safe_mode_banner_load_plugins),
                onClick = onClickLoadPlugins,
                style = JwButtonStyle.Text,
            )
        },
    )
}

@Preview
@Composable
private fun UncleanExitBannerPreview() {
    UncleanExitBanner(
        report = UncleanExitReport(
            pid = 65669,
            startedAtMillis = 0,
            duringStartup = false,
            crashLog = JvmCrashLog(
                path = "/Users/me/.jetwhale/logs/hs_err_pid65669.log",
                errorLine = "SIGSEGV (0xb) at pc=0x000000014957d3d0, pid=65669, tid=130819",
                problematicFrame = "C  [libskiko-macos-arm64.dylib+0x1053d0]  SkBitmap::notifyPixelsChanged() const+0x0",
                crashingThread = null,
                javaFrames = emptyList(),
            ),
            suspectedPlugin = SuspectedPlugin(pluginId = "com.example.mirror", pluginName = "Device Mirror"),
            logsDirectory = "/Users/me/.jetwhale/logs",
        ),
        onClickOpenCrashLog = {},
        onClickOpenLogs = {},
        onClickDisablePlugin = {},
        onDismiss = {},
    )
}

@Preview
@Composable
private fun SafeModeBannerPreview() {
    SafeModeBanner(
        safeMode = SafeMode(SafeModeReason.RepeatedStartupCrashes),
        onClickLoadPlugins = {},
    )
}
