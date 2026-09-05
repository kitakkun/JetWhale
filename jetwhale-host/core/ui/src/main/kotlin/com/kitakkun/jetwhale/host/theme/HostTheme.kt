package com.kitakkun.jetwhale.host.theme

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.model.JetWhaleColorScheme
import com.kitakkun.jetwhale.host.ui.JwTheme

/** Applies the host's configured [JetWhaleColorScheme] as a [JwTheme]. */
@Composable
fun HostTheme(
    colorScheme: JetWhaleColorScheme,
    content: @Composable () -> Unit,
) {
    JwTheme(
        colorScheme = colorScheme.toMaterial3ColorScheme(),
        darkTheme = colorScheme.isDarkTheme(),
        content = content,
    )
}
