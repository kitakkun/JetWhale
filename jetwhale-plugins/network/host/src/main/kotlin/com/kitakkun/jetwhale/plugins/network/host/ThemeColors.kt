package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Returns [dark] under a dark theme and [light] otherwise, so fixed brand hues stay legible against
 * both surfaces while keeping their hue.
 */
@Composable
internal fun themeColor(light: Color, dark: Color): Color = if (isSystemInDarkTheme()) dark else light
