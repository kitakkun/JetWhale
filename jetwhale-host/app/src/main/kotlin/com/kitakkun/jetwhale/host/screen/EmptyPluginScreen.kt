package com.kitakkun.jetwhale.host.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.no_plugin_selected
import org.jetbrains.compose.resources.stringResource

@Composable
fun EmptyPluginScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(Res.string.no_plugin_selected))
    }
}
