package com.kitakkun.jetwhale.host.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.kitakkun.jetwhale.host.sdk.LocalJetWhaleDarkTheme

private val LocalJwExtendedColors = staticCompositionLocalOf<JwExtendedColors> {
    error("JwTheme is not applied above this composable")
}

/**
 * The JetWhale look: a Material 3 theme configured for a desktop tool window (compact type scale,
 * tight corners, neutral surfaces) plus the [JwExtendedColors] the components draw with.
 *
 * The host applies it around every plugin's `Content()`, so a plugin never calls this itself — it
 * reads [JwTheme.colors] and the ordinary `MaterialTheme` accessors, which resolve to the
 * host's applied scheme. Calling it inside a plugin is only useful to deliberately re-theme a
 * subtree.
 *
 * @param colorScheme the Material color scheme to apply; the built-in ones are [JwColorSchemes].
 * @param darkTheme whether [colorScheme] is a dark scheme. Published through [LocalJetWhaleDarkTheme],
 * so it decides which fixed tones the extended colors use.
 * @param content the UI to theme.
 */
@Composable
public fun JwTheme(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val extendedColors = remember(colorScheme, darkTheme) { JwExtendedColors.from(colorScheme, darkTheme) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = JwTypography.material(),
        shapes = JwShapes.material(),
    ) {
        CompositionLocalProvider(
            LocalJwExtendedColors provides extendedColors,
            LocalJetWhaleDarkTheme provides darkTheme,
            LocalContentColor provides colorScheme.onSurface,
            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
            content = content,
        )
    }
}

/** Accessors for the values the enclosing [JwTheme] applied. */
public object JwTheme {
    /** The extended colors derived for the applied scheme. */
    public val colors: JwExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalJwExtendedColors.current

    /** Whether the enclosing theme is dark; the same value as [LocalJetWhaleDarkTheme]. */
    public val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalJetWhaleDarkTheme.current
}
