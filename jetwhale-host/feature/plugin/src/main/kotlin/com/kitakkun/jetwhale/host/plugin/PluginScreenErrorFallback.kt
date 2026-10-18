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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import org.jetbrains.compose.resources.stringResource
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PluginScreenErrorFallback(
    title: String,
    hint: String?,
    pluginId: String,
    cause: Throwable,
    onClickReset: (() -> Unit)?,
) {
    val clipboard = LocalClipboard.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JwText(
            text = title,
            style = JwTheme.textStyles.title,
        )
        if (hint != null) {
            JwText(
                text = hint,
                style = JwTheme.textStyles.body,
            )
        }
        JwText(
            text = stringResource(Res.string.plugin_ui_crash_plugin_id, pluginId),
            style = JwTheme.textStyles.body,
        )
        JwText(
            text = stringResource(Res.string.plugin_ui_crash_error_message, cause.localizedMessage),
            style = JwTheme.textStyles.bodySmall,
        )
        JwText(
            text = stringResource(Res.string.plugin_ui_crash_stacktrace, cause.stackTraceToString()),
            style = JwTheme.textStyles.bodySmall,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
            JwButton(
                text = stringResource(Res.string.plugin_ui_crash_copy_full_stacktrace),
                onClick = {
                    clipboard.awtClipboard?.setContents(
                        StringSelection(cause.stackTraceToString()),
                        null,
                    )
                },
            )
            if (onClickReset != null) {
                JwButton(
                    text = stringResource(Res.string.plugin_ui_crash_reload),
                    onClick = onClickReset,
                    style = JwButtonStyle.Primary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PluginScreenErrorFallbackPreview() {
    JwTheme(darkTheme = false) {
        PluginScreenErrorFallback(
            title = stringResource(Res.string.plugin_ui_crash_title),
            hint = null,
            pluginId = "com.example.sample-plugin",
            onClickReset = {},
            cause = IllegalStateException("The plugin UI threw while composing"),
        )
    }
}
