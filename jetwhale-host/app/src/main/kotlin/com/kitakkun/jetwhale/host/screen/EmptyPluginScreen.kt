package com.kitakkun.jetwhale.host.screen

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.no_plugin_selected
import com.kitakkun.jetwhale.host.no_plugin_selected_hint
import com.kitakkun.jetwhale.host.puzzle_outlined
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwIcon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun EmptyPluginScreen() {
    JwEmptyState(
        title = stringResource(Res.string.no_plugin_selected),
        description = stringResource(Res.string.no_plugin_selected_hint),
        icon = {
            JwIcon(
                painter = painterResource(Res.drawable.puzzle_outlined),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        },
    )
}
