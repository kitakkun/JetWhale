package com.kitakkun.jetwhale.debugger.host.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.awtClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import soil.plant.compose.reacty.ErrorBoundaryContext
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PluginScreenErrorFallback(
    pluginId: String,
    onClickReset: () -> Unit,
    errorBoundaryContext: ErrorBoundaryContext,
) {
    val clipboard = LocalClipboard.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "The Plugin UI Crashed",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "PluginId: $pluginId",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Error Message: ${errorBoundaryContext.err.localizedMessage}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Full Stack Trace:\n${errorBoundaryContext.err.stackTraceToString()}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
        )
        Button(
            onClick = {
                clipboard.awtClipboard?.setContents(
                    StringSelection(errorBoundaryContext.err.stackTraceToString()),
                    null
                )
            }
        ) {
            Text("Copy stack trace")
        }
        Button(
            onClick = onClickReset,
        ) {
            Text("Reload Plugin UI (Not working yet)")
        }
    }
}
