package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kitakkun.jetwhale.host.sdk.LocalJetWhaleDarkTheme

/**
 * True when the host is rendering this plugin in a dark theme. Reads the authoritative
 * [LocalJetWhaleDarkTheme] the host provides from its actually-applied color scheme, not the OS
 * setting (`isSystemInDarkTheme`) — the host has its own Theme option (builtin:light / builtin:dark
 * / builtin:dynamic), which can disagree with what's on screen.
 */
@Composable
internal fun isDarkTheme(): Boolean = LocalJetWhaleDarkTheme.current

/**
 * Returns [dark] under a dark theme and [light] otherwise, so fixed brand hues stay legible against
 * both surfaces while keeping their hue.
 */
@Composable
internal fun themeColor(light: Color, dark: Color): Color = if (isDarkTheme()) dark else light
