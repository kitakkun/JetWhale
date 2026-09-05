package com.kitakkun.jetwhale.host.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.kitakkun.jetwhale.host.model.JetWhaleColorScheme

@Composable
fun JetWhaleTheme(
    colorScheme: JetWhaleColorScheme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme.toMaterial3ColorScheme(),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) {
            content()
        }
    }
}
