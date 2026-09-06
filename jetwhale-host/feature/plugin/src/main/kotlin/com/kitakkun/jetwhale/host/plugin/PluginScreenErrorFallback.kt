package com.kitakkun.jetwhale.host.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.awtClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
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
        JwText(
            text = stringResource(Res.string.plugin_ui_crash_title),
            style = JwTheme.textStyles.title,
        )
        JwText(
            text = stringResource(Res.string.plugin_ui_crash_plugin_id, pluginId),
            style = JwTheme.textStyles.body,
        )
        JwText(
            text = stringResource(Res.string.plugin_ui_crash_error_message, errorBoundaryContext.err.localizedMessage),
            style = JwTheme.textStyles.bodySmall,
        )
        JwText(
            text = stringResource(Res.string.plugin_ui_crash_stacktrace, errorBoundaryContext.err.stackTraceToString()),
            style = JwTheme.textStyles.bodySmall,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
            JwButton(
                text = stringResource(Res.string.plugin_ui_crash_copy_full_stacktrace),
                onClick = {
                    clipboard.awtClipboard?.setContents(
                        StringSelection(errorBoundaryContext.err.stackTraceToString()),
                        null,
                    )
                },
            )
            JwButton(
                text = stringResource(Res.string.plugin_ui_crash_reload),
                onClick = onClickReset,
                style = JwButtonStyle.Primary,
            )
        }
    }
}
