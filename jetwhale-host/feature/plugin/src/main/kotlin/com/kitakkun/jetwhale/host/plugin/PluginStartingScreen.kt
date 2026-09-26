package com.kitakkun.jetwhale.host.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwProgressIndicator
import com.kitakkun.jetwhale.host.ui.JwProgressIndicatorDefaults
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Shown while a plugin screen waits for its instance: the screen can open before the instance exists
 * (a plugin that needs no app, right after the host starts or the plugin is switched on), and the
 * scene replaces this as soon as the instance appears.
 */
@Composable
fun PluginStartingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JwProgressIndicator(size = JwProgressIndicatorDefaults.largeSize)
        JwText(
            text = stringResource(Res.string.plugin_starting),
            style = JwTheme.textStyles.body,
            color = JwTheme.colors.textSecondary,
        )
    }
}

@Preview
@Composable
private fun PluginStartingScreenPreview() {
    PluginStartingScreen()
}
