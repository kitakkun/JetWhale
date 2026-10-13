package com.kitakkun.jetwhale.tools.docsscreenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.model.JetWhaleColorScheme
import com.kitakkun.jetwhale.host.theme.HostTheme
import com.kitakkun.jetwhale.host.ui.JwSurface

/**
 * What the host window draws in: the built-in color scheme for [darkTheme] as the host applies it,
 * Material layer included, and a surface filling the window.
 */
// A frame around the host screen it is given, with no look of its own to preview.
@Suppress("KOTRAIL_COMPOSABLE_WITHOUT_PREVIEW")
@Composable
fun HostWindowSurface(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    HostTheme(colorScheme = if (darkTheme) JetWhaleColorScheme.Static.Dark else JetWhaleColorScheme.Static.Light) {
        JwSurface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
