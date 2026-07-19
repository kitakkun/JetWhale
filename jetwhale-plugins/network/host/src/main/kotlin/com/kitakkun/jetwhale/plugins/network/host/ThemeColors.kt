package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * True when the **applied** Material theme is dark — derived from the color scheme's own surface
 * luminance, not the OS setting (`isSystemInDarkTheme`). The host has its own Theme option
 * (builtin:light / builtin:dark / builtin:dynamic), so the OS value can disagree with what's on
 * screen; reading the actual scheme keeps these colors in step with the in-app theme.
 */
@Composable
internal fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * Returns [dark] under a dark theme and [light] otherwise, so fixed brand hues stay legible against
 * both surfaces while keeping their hue.
 */
@Composable
internal fun themeColor(light: Color, dark: Color): Color = if (isDarkTheme()) dark else light
