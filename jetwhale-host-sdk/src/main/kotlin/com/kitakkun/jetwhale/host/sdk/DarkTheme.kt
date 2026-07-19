package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * True when the host is rendering this plugin in a dark theme.
 *
 * Consume this (e.g. `LocalJetWhaleDarkTheme.current`) to pick theme-appropriate colors, instead of
 * `isSystemInDarkTheme()` — that reflects the OS setting, which can disagree with the host's own
 * Theme option (builtin:light / builtin:dark / builtin:dynamic). The host provides the authoritative
 * value from its actually-applied color scheme.
 */
public val LocalJetWhaleDarkTheme: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }
