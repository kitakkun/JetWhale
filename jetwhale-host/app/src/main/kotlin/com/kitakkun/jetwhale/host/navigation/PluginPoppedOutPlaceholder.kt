package com.kitakkun.jetwhale.host.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PluginPoppedOutPlaceholder(
    onBringbackToMainWindow: () -> Unit,
) {
    Surface {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("This plugin is popped out. Please check the separate window.")
            Button(onClick = onBringbackToMainWindow) {
                Text("Bring back to main window")
            }
        }
    }
}
