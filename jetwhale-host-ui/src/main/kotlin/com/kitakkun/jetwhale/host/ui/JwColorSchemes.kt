package com.kitakkun.jetwhale.host.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The built-in color schemes of the JetWhale host: neutral surfaces with a single blue accent, the
 * palette of a desktop tool rather than the tinted baseline Material 3 ships with. Chrome such as the
 * sidebar and toolbars is drawn on the `surfaceContainer*` steps, so the steps are spaced closely
 * enough to read as panels of one window instead of stacked cards.
 */
public object JwColorSchemes {
    public fun light(): ColorScheme = lightColorScheme(
        primary = Color(0xFF2F6FE4),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE7FB),
        onPrimaryContainer = Color(0xFF0B2F6B),
        inversePrimary = Color(0xFF9DBDF6),
        secondary = Color(0xFF5D6270),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE6E8ED),
        onSecondaryContainer = Color(0xFF2A2D34),
        tertiary = Color(0xFF1A8F7A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCDEFE7),
        onTertiaryContainer = Color(0xFF07352D),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1D1F23),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1D1F23),
        surfaceVariant = Color(0xFFE9EBEF),
        onSurfaceVariant = Color(0xFF5D6270),
        surfaceTint = Color(0xFF2F6FE4),
        inverseSurface = Color(0xFF2D2F34),
        inverseOnSurface = Color(0xFFF1F2F5),
        error = Color(0xFFD93025),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBE0DD),
        onErrorContainer = Color(0xFF5F1410),
        outline = Color(0xFFB8BDC7),
        outlineVariant = Color(0xFFDFE2E8),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFE2E5EA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F8FA),
        surfaceContainer = Color(0xFFF1F2F5),
        surfaceContainerHigh = Color(0xFFEAECF0),
        surfaceContainerHighest = Color(0xFFE2E5EA),
    )

    public fun dark(): ColorScheme = darkColorScheme(
        primary = Color(0xFF6A9BF5),
        onPrimary = Color(0xFF0B2247),
        primaryContainer = Color(0xFF1F3A6B),
        onPrimaryContainer = Color(0xFFD6E3FF),
        inversePrimary = Color(0xFF2F6FE4),
        secondary = Color(0xFFA5A9B4),
        onSecondary = Color(0xFF1E1F22),
        secondaryContainer = Color(0xFF35373D),
        onSecondaryContainer = Color(0xFFE6E7EA),
        tertiary = Color(0xFF5CC9B3),
        onTertiary = Color(0xFF07352D),
        tertiaryContainer = Color(0xFF15574A),
        onTertiaryContainer = Color(0xFFCDEFE7),
        background = Color(0xFF1E1F22),
        onBackground = Color(0xFFE6E7EA),
        surface = Color(0xFF1E1F22),
        onSurface = Color(0xFFE6E7EA),
        surfaceVariant = Color(0xFF2F3136),
        onSurfaceVariant = Color(0xFFA5A9B4),
        surfaceTint = Color(0xFF6A9BF5),
        inverseSurface = Color(0xFFE6E7EA),
        inverseOnSurface = Color(0xFF2D2F34),
        error = Color(0xFFF0655D),
        onError = Color(0xFF3B0907),
        errorContainer = Color(0xFF5F1410),
        onErrorContainer = Color(0xFFFBE0DD),
        outline = Color(0xFF5A5E69),
        outlineVariant = Color(0xFF3A3D45),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF35373D),
        surfaceDim = Color(0xFF191A1D),
        surfaceContainerLowest = Color(0xFF191A1D),
        surfaceContainerLow = Color(0xFF222327),
        surfaceContainer = Color(0xFF27282C),
        surfaceContainerHigh = Color(0xFF2D2F34),
        surfaceContainerHighest = Color(0xFF35373D),
    )
}
