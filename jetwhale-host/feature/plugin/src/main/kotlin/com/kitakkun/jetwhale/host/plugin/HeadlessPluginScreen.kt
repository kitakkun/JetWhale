package com.kitakkun.jetwhale.host.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Shown in place of a plugin scene when the plugin renders no UI. The host would otherwise draw an
 * empty canvas here, which reads as a broken plugin rather than an intentional one.
 */
@Composable
fun HeadlessPluginScreen(pluginId: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JwText(
            text = stringResource(Res.string.headless_plugin_title),
            style = JwTheme.textStyles.title,
        )
        JwText(
            text = stringResource(Res.string.headless_plugin_description),
            style = JwTheme.textStyles.body,
            color = JwTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        JwText(
            text = stringResource(Res.string.headless_plugin_plugin_id, pluginId),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
    }
}
