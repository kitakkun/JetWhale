package com.kitakkun.jetwhale.host.navigation

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.bring_back_to_main_window
import com.kitakkun.jetwhale.host.plugin_popped_out_message
import org.jetbrains.compose.resources.stringResource

@Composable
fun PluginPoppedOutScreen(
    onBringbackToMainWindow: () -> Unit,
) {
    Surface {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(Res.string.plugin_popped_out_message))
            Button(onClick = onBringbackToMainWindow) {
                Text(stringResource(Res.string.bring_back_to_main_window))
            }
        }
    }
}

@Preview
@Composable
private fun PluginPoppedOutScreenPreview() {
    PluginPoppedOutScreen(
        onBringbackToMainWindow = {},
    )
}