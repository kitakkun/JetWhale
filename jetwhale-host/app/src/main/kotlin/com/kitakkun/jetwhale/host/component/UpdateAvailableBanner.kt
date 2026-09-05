package com.kitakkun.jetwhale.host.component

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.update_banner_message
import com.kitakkun.jetwhale.host.update_banner_open_settings
import org.jetbrains.compose.resources.stringResource

/**
 * Startup notification for a newer host release. Notify-only by design: installing
 * always happens through the settings screen with an explicit user action.
 */
@Composable
fun UpdateAvailableBanner(
    latestVersion: String,
    onClickOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    JwBanner(
        text = stringResource(Res.string.update_banner_message, latestVersion),
        tone = JwTone.Info,
        actions = {
            JwButton(
                text = stringResource(Res.string.update_banner_open_settings),
                onClick = onClickOpenSettings,
                style = JwButtonStyle.Text,
            )
        },
        onDismiss = onDismiss,
    )
}
