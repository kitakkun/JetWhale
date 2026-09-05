package com.kitakkun.jetwhale.host.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.bring_back_to_main_window
import com.kitakkun.jetwhale.host.plugin_popped_out_message
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwIcon
import org.jetbrains.compose.resources.stringResource

@Composable
fun PluginPoppedOutScreen(
    onBringbackToMainWindow: () -> Unit,
) {
    JwEmptyState(
        title = stringResource(Res.string.plugin_popped_out_message),
        icon = { JwIcon(imageVector = Icons.Default.ArrowOutward, contentDescription = null) },
        action = {
            JwButton(
                text = stringResource(Res.string.bring_back_to_main_window),
                onClick = onBringbackToMainWindow,
                style = JwButtonStyle.Primary,
            )
        },
    )
}

@Preview
@Composable
private fun PluginPoppedOutScreenPreview() {
    PluginPoppedOutScreen(
        onBringbackToMainWindow = {},
    )
}
