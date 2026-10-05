package com.kitakkun.jetwhale.host.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.close
import com.kitakkun.jetwhale.host.host_restart_failed_banner
import com.kitakkun.jetwhale.host.host_set_aside_banner
import com.kitakkun.jetwhale.host.host_set_aside_try_again
import com.kitakkun.jetwhale.host.host_set_aside_view_log
import com.kitakkun.jetwhale.host.host_update_banner_available
import com.kitakkun.jetwhale.host.host_update_banner_open_settings
import com.kitakkun.jetwhale.host.host_update_banner_ready
import com.kitakkun.jetwhale.host.host_update_banner_restart
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import org.jetbrains.compose.resources.stringResource

/**
 * The startup notice for a newer host: one that can be downloaded, or one installed and waiting
 * for a restart. Notify only; a download starts in Settings, on the user's click.
 */
@Composable
fun HostUpdateBanner(
    versionName: String,
    isInstalled: Boolean,
    onClickOpenSettings: () -> Unit,
    onClickRestart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwBanner(
        text = stringResource(if (isInstalled) Res.string.host_update_banner_ready else Res.string.host_update_banner_available, versionName),
        modifier = modifier,
        tone = JwTone.Info,
        actions = {
            if (isInstalled) {
                JwButton(text = stringResource(Res.string.host_update_banner_restart), onClick = onClickRestart, style = JwButtonStyle.Text)
            } else {
                JwButton(text = stringResource(Res.string.host_update_banner_open_settings), onClick = onClickOpenSettings, style = JwButtonStyle.Text)
            }
        },
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.close),
    )
}

/**
 * Names the version the launcher set aside during this launch because it failed its first starts,
 * links its output, and offers to try it again.
 */
@Composable
fun HostSetAsideBanner(
    setAsideVersionName: String,
    runningVersionName: String,
    onClickViewLog: () -> Unit,
    onClickTryAgain: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwBanner(
        text = stringResource(Res.string.host_set_aside_banner, setAsideVersionName, runningVersionName),
        modifier = modifier,
        tone = JwTone.Warning,
        actions = {
            JwButton(text = stringResource(Res.string.host_set_aside_view_log), onClick = onClickViewLog, style = JwButtonStyle.Text)
            JwButton(text = stringResource(Res.string.host_set_aside_try_again), onClick = onClickTryAgain, style = JwButtonStyle.Text)
        },
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.close),
    )
}

/** Says that *Restart to update* or *Try again* could not restart the host, and how to restart it by hand. */
@Composable
fun HostRestartFailedBanner(modifier: Modifier = Modifier) {
    JwBanner(text = stringResource(Res.string.host_restart_failed_banner), modifier = modifier, tone = JwTone.Error)
}

@Preview
@Composable
private fun HostUpdateBannerPreview() {
    JwTheme(darkTheme = false) {
        HostUpdateBanner(
            versionName = "1.0.0-alpha14",
            isInstalled = false,
            onClickOpenSettings = {},
            onClickRestart = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun HostSetAsideBannerPreview() {
    JwTheme(darkTheme = false) {
        HostSetAsideBanner(
            setAsideVersionName = "1.0.0-alpha15",
            runningVersionName = "1.0.0-alpha14",
            onClickViewLog = {},
            onClickTryAgain = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun HostRestartFailedBannerPreview() {
    JwTheme(darkTheme = false) {
        HostRestartFailedBanner()
    }
}
