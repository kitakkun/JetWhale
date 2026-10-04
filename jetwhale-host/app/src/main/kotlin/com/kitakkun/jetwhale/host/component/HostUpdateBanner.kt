package com.kitakkun.jetwhale.host.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.close
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
    version: String,
    isInstalled: Boolean,
    onClickOpenSettings: () -> Unit,
    onClickRestart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwBanner(
        text = stringResource(if (isInstalled) Res.string.host_update_banner_ready else Res.string.host_update_banner_available, version),
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
    setAsideVersion: String,
    runningVersion: String,
    onClickViewLog: () -> Unit,
    onClickTryAgain: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwBanner(
        text = stringResource(Res.string.host_set_aside_banner, setAsideVersion, runningVersion),
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

@Preview
@Composable
private fun HostUpdateBannerPreview() {
    JwTheme(darkTheme = false) {
        HostUpdateBanner(
            version = "1.0.0-alpha14",
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
            setAsideVersion = "1.0.0-alpha15",
            runningVersion = "1.0.0-alpha14",
            onClickViewLog = {},
            onClickTryAgain = {},
            onDismiss = {},
        )
    }
}
