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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
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
 * A token left [Color.Unspecified] — a user theme that declares only some of them does that — is
 * filled from the built-in scheme for [darkTheme], so no component draws with an unspecified color.
 * @param darkTheme whether [colorScheme] is a dark scheme. Published through [LocalJetWhaleDarkTheme],
 * so it decides which fixed tones the extended colors use.
 * @param extendedColors the colors beyond Material's; derived from [colorScheme] with
 * [JwExtendedColors.from], and a theme with tones of its own passes a [JwExtendedColors.copy].
 * @param content the UI to theme.
 */
@Composable
public fun JwTheme(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    extendedColors: JwExtendedColors = JwExtendedColors.from(colorScheme.withUnspecifiedFilled(darkTheme), darkTheme),
    content: @Composable () -> Unit,
) {
    val resolvedScheme = remember(colorScheme, darkTheme) { colorScheme.withUnspecifiedFilled(darkTheme) }
    MaterialTheme(
        colorScheme = resolvedScheme,
        typography = JwTypography.material(),
        shapes = JwShapes.material(),
    ) {
        CompositionLocalProvider(
            LocalJwExtendedColors provides extendedColors,
            LocalJetWhaleDarkTheme provides darkTheme,
            LocalContentColor provides resolvedScheme.onSurface,
            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
            content = content,
        )
    }
}

/** Every token of this scheme, with the unspecified ones taken from the built-in scheme for [darkTheme]. */
private fun ColorScheme.withUnspecifiedFilled(darkTheme: Boolean): ColorScheme {
    val fallback = if (darkTheme) JwColorSchemes.dark() else JwColorSchemes.light()
    return copy(
        primary = primary.takeOrElse { fallback.primary },
        onPrimary = onPrimary.takeOrElse { fallback.onPrimary },
        primaryContainer = primaryContainer.takeOrElse { fallback.primaryContainer },
        onPrimaryContainer = onPrimaryContainer.takeOrElse { fallback.onPrimaryContainer },
        inversePrimary = inversePrimary.takeOrElse { fallback.inversePrimary },
        secondary = secondary.takeOrElse { fallback.secondary },
        onSecondary = onSecondary.takeOrElse { fallback.onSecondary },
        secondaryContainer = secondaryContainer.takeOrElse { fallback.secondaryContainer },
        onSecondaryContainer = onSecondaryContainer.takeOrElse { fallback.onSecondaryContainer },
        tertiary = tertiary.takeOrElse { fallback.tertiary },
        onTertiary = onTertiary.takeOrElse { fallback.onTertiary },
        tertiaryContainer = tertiaryContainer.takeOrElse { fallback.tertiaryContainer },
        onTertiaryContainer = onTertiaryContainer.takeOrElse { fallback.onTertiaryContainer },
        background = background.takeOrElse { fallback.background },
        onBackground = onBackground.takeOrElse { fallback.onBackground },
        surface = surface.takeOrElse { fallback.surface },
        onSurface = onSurface.takeOrElse { fallback.onSurface },
        surfaceVariant = surfaceVariant.takeOrElse { fallback.surfaceVariant },
        onSurfaceVariant = onSurfaceVariant.takeOrElse { fallback.onSurfaceVariant },
        surfaceTint = surfaceTint.takeOrElse { fallback.surfaceTint },
        inverseSurface = inverseSurface.takeOrElse { fallback.inverseSurface },
        inverseOnSurface = inverseOnSurface.takeOrElse { fallback.inverseOnSurface },
        error = error.takeOrElse { fallback.error },
        onError = onError.takeOrElse { fallback.onError },
        errorContainer = errorContainer.takeOrElse { fallback.errorContainer },
        onErrorContainer = onErrorContainer.takeOrElse { fallback.onErrorContainer },
        outline = outline.takeOrElse { fallback.outline },
        outlineVariant = outlineVariant.takeOrElse { fallback.outlineVariant },
        scrim = scrim.takeOrElse { fallback.scrim },
        surfaceBright = surfaceBright.takeOrElse { fallback.surfaceBright },
        surfaceDim = surfaceDim.takeOrElse { fallback.surfaceDim },
        surfaceContainer = surfaceContainer.takeOrElse { fallback.surfaceContainer },
        surfaceContainerHigh = surfaceContainerHigh.takeOrElse { fallback.surfaceContainerHigh },
        surfaceContainerHighest = surfaceContainerHighest.takeOrElse { fallback.surfaceContainerHighest },
        surfaceContainerLow = surfaceContainerLow.takeOrElse { fallback.surfaceContainerLow },
        surfaceContainerLowest = surfaceContainerLowest.takeOrElse { fallback.surfaceContainerLowest },
        primaryFixed = primaryFixed.takeOrElse { fallback.primaryFixed },
        primaryFixedDim = primaryFixedDim.takeOrElse { fallback.primaryFixedDim },
        onPrimaryFixed = onPrimaryFixed.takeOrElse { fallback.onPrimaryFixed },
        onPrimaryFixedVariant = onPrimaryFixedVariant.takeOrElse { fallback.onPrimaryFixedVariant },
        secondaryFixed = secondaryFixed.takeOrElse { fallback.secondaryFixed },
        secondaryFixedDim = secondaryFixedDim.takeOrElse { fallback.secondaryFixedDim },
        onSecondaryFixed = onSecondaryFixed.takeOrElse { fallback.onSecondaryFixed },
        onSecondaryFixedVariant = onSecondaryFixedVariant.takeOrElse { fallback.onSecondaryFixedVariant },
        tertiaryFixed = tertiaryFixed.takeOrElse { fallback.tertiaryFixed },
        tertiaryFixedDim = tertiaryFixedDim.takeOrElse { fallback.tertiaryFixedDim },
        onTertiaryFixed = onTertiaryFixed.takeOrElse { fallback.onTertiaryFixed },
        onTertiaryFixedVariant = onTertiaryFixedVariant.takeOrElse { fallback.onTertiaryFixedVariant },
    )
}

/**
 * [JwTheme] with the built-in scheme for [darkTheme]: the light one from [JwColorSchemes.light],
 * the dark one from [JwColorSchemes.dark]. For previews and tests of a plugin's UI, where the host
 * is not there to supply its configured scheme.
 *
 * @param darkTheme which built-in scheme to apply.
 * @param content the UI to theme.
 */
@Composable
public fun JwTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    JwTheme(
        colorScheme = if (darkTheme) JwColorSchemes.dark() else JwColorSchemes.light(),
        darkTheme = darkTheme,
        content = content,
    )
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
