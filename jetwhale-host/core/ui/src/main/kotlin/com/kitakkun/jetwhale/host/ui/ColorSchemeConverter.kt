package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.kitakkun.jetwhale.host.model.JetWhaleColorScheme
import com.kitakkun.jetwhale.host.model.ThemeColorTokens

@Composable
fun JetWhaleColorScheme.toMaterial3ColorScheme(): ColorScheme {
    return when (this) {
        is JetWhaleColorScheme.Dynamic -> {
            if (isSystemInDarkTheme()) {
                darkColorScheme.toMaterial3ColorScheme()
            } else {
                lightColorScheme.toMaterial3ColorScheme()
            }
        }

        is JetWhaleColorScheme.Static.Light -> lightColorScheme()
        is JetWhaleColorScheme.Static.Dark -> darkColorScheme()
        is JetWhaleColorScheme.Static.Custom -> {
            remember(this) {
                val colors = this.colors.mapValues { Color(it.value) }
                ColorScheme(
                    primary = colors[ThemeColorTokens.Primary] ?: Color.Unspecified,
                    onPrimary = colors[ThemeColorTokens.OnPrimary] ?: Color.Unspecified,
                    primaryContainer = colors[ThemeColorTokens.PrimaryContainer] ?: Color.Unspecified,
                    onPrimaryContainer = colors[ThemeColorTokens.OnPrimaryContainer] ?: Color.Unspecified,
                    inversePrimary = colors[ThemeColorTokens.InversePrimary] ?: Color.Unspecified,
                    secondary = colors[ThemeColorTokens.Secondary] ?: Color.Unspecified,
                    onSecondary = colors[ThemeColorTokens.OnSecondary] ?: Color.Unspecified,
                    secondaryContainer = colors[ThemeColorTokens.SecondaryContainer] ?: Color.Unspecified,
                    onSecondaryContainer = colors[ThemeColorTokens.OnSecondaryContainer] ?: Color.Unspecified,
                    tertiary = colors[ThemeColorTokens.Tertiary] ?: Color.Unspecified,
                    onTertiary = colors[ThemeColorTokens.OnTertiary] ?: Color.Unspecified,
                    tertiaryContainer = colors[ThemeColorTokens.TertiaryContainer] ?: Color.Unspecified,
                    onTertiaryContainer = colors[ThemeColorTokens.OnTertiaryContainer] ?: Color.Unspecified,
                    background = colors[ThemeColorTokens.Background] ?: Color.Unspecified,
                    onBackground = colors[ThemeColorTokens.OnBackground] ?: Color.Unspecified,
                    surface = colors[ThemeColorTokens.Surface] ?: Color.Unspecified,
                    onSurface = colors[ThemeColorTokens.OnSurface] ?: Color.Unspecified,
                    surfaceVariant = colors[ThemeColorTokens.SurfaceVariant] ?: Color.Unspecified,
                    onSurfaceVariant = colors[ThemeColorTokens.OnSurfaceVariant] ?: Color.Unspecified,
                    surfaceTint = colors[ThemeColorTokens.SurfaceTint] ?: Color.Unspecified,
                    inverseSurface = colors[ThemeColorTokens.InverseSurface] ?: Color.Unspecified,
                    inverseOnSurface = colors[ThemeColorTokens.InverseOnSurface] ?: Color.Unspecified,
                    error = colors[ThemeColorTokens.Error] ?: Color.Unspecified,
                    onError = colors[ThemeColorTokens.OnError] ?: Color.Unspecified,
                    errorContainer = colors[ThemeColorTokens.ErrorContainer] ?: Color.Unspecified,
                    onErrorContainer = colors[ThemeColorTokens.OnErrorContainer] ?: Color.Unspecified,
                    outline = colors[ThemeColorTokens.Outline] ?: Color.Unspecified,
                    outlineVariant = colors[ThemeColorTokens.OutlineVariant] ?: Color.Unspecified,
                    scrim = colors[ThemeColorTokens.Scrim] ?: Color.Unspecified,
                    surfaceBright = colors[ThemeColorTokens.SurfaceBright] ?: Color.Unspecified,
                    surfaceDim = colors[ThemeColorTokens.SurfaceDim] ?: Color.Unspecified,
                    surfaceContainer = colors[ThemeColorTokens.SurfaceContainer] ?: Color.Unspecified,
                    surfaceContainerHigh = colors[ThemeColorTokens.SurfaceContainerHigh] ?: Color.Unspecified,
                    surfaceContainerHighest = colors[ThemeColorTokens.SurfaceContainerHighest] ?: Color.Unspecified,
                    surfaceContainerLow = colors[ThemeColorTokens.SurfaceContainerLow] ?: Color.Unspecified,
                    surfaceContainerLowest = colors[ThemeColorTokens.SurfaceContainerLowest] ?: Color.Unspecified,
                    primaryFixed = colors[ThemeColorTokens.PrimaryFixed] ?: Color.Unspecified,
                    primaryFixedDim = colors[ThemeColorTokens.PrimaryFixedDim] ?: Color.Unspecified,
                    onPrimaryFixed = colors[ThemeColorTokens.OnPrimaryFixed] ?: Color.Unspecified,
                    onPrimaryFixedVariant = colors[ThemeColorTokens.OnPrimaryFixedVariant] ?: Color.Unspecified,
                    secondaryFixed = colors[ThemeColorTokens.SecondaryFixed] ?: Color.Unspecified,
                    secondaryFixedDim = colors[ThemeColorTokens.SecondaryFixedDim] ?: Color.Unspecified,
                    onSecondaryFixed = colors[ThemeColorTokens.OnSecondaryFixed] ?: Color.Unspecified,
                    onSecondaryFixedVariant = colors[ThemeColorTokens.OnSecondaryFixedVariant] ?: Color.Unspecified,
                    tertiaryFixed = colors[ThemeColorTokens.TertiaryFixed] ?: Color.Unspecified,
                    tertiaryFixedDim = colors[ThemeColorTokens.TertiaryFixedDim] ?: Color.Unspecified,
                    onTertiaryFixed = colors[ThemeColorTokens.OnTertiaryFixed] ?: Color.Unspecified,
                    onTertiaryFixedVariant = colors[ThemeColorTokens.OnTertiaryFixedVariant] ?: Color.Unspecified,
                )
            }
        }
    }
}
