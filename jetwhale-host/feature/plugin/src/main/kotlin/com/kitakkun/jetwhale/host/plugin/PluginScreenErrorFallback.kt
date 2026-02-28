package com.kitakkun.jetwhale.host.plugin

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
import org.jetbrains.compose.resources.stringResource
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
            text = stringResource(Res.string.plugin_ui_crash_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(Res.string.plugin_ui_crash_plugin_id, pluginId),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(Res.string.plugin_ui_crash_error_message, errorBoundaryContext.err.localizedMessage),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(Res.string.plugin_ui_crash_stacktrace, errorBoundaryContext.err.stackTraceToString()),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
        )
        Button(
            onClick = {
                clipboard.awtClipboard?.setContents(
                    StringSelection(errorBoundaryContext.err.stackTraceToString()),
                    null,
                )
            },
        ) {
            Text(stringResource(Res.string.plugin_ui_crash_copy_full_stacktrace))
        }
        Button(
            onClick = onClickReset,
        ) {
            Text(stringResource(Res.string.plugin_ui_crash_reload))
        }
    }
}
