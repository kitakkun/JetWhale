package com.kitakkun.jetwhale.host.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.sp
import com.kitakkun.jetwhale.host.model.JetWhaleColorScheme
import com.kitakkun.jetwhale.host.ui.JwColors
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwTextStyles
import com.kitakkun.jetwhale.host.ui.JwTheme

/**
 * Applies the host's configured [JetWhaleColorScheme] as a [JwTheme], and inside it a Material
 * theme derived from the same colors.
 *
 * The Material layer is for content the library does not cover: the host's own screens that still
 * use Material widgets, and plugins written against Material 3 before `jetwhale-host-ui` existed.
 * `jetwhale-host-ui` itself depends on nothing Material; the bridge lives here so that dependency
 * stays in the host.
 */
@Composable
fun HostTheme(
    colorScheme: JetWhaleColorScheme,
    content: @Composable () -> Unit,
) {
    val material = colorScheme.toMaterial3ColorScheme()
    val dark = colorScheme.isDarkTheme()
    val jwColors = remember(material, dark) { material.toJwColors(dark) }
    JwTheme(colors = jwColors) {
        MaterialTheme(
            colorScheme = material,
            typography = remember { materialTypography(JwTextStyles.default()) },
            shapes = remember { materialShapes() },
        ) {
            CompositionLocalProvider(
                LocalContentColor provides material.onSurface,
                LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                content = content,
            )
        }
    }
}

/**
 * Maps the host's Material-shaped theme model onto [JwColors]. Starts from the built-in scheme
 * for [dark] and overrides what the model specifies, so a partial custom theme keeps a coherent
 * look; a token left `Color.Unspecified` falls back to the built-in value.
 */
private fun ColorScheme.toJwColors(dark: Boolean): JwColors {
    val base = if (dark) JwColors.dark() else JwColors.light()
    return base.copy(
        surface = surface.takeOrElse { base.surface },
        onSurface = onSurface.takeOrElse { base.onSurface },
        textSecondary = onSurfaceVariant.takeOrElse { base.textSecondary },
        textDisabled = onSurface.takeOrElse { base.onSurface }.copy(alpha = DISABLED_ALPHA),
        sidebarBackground = surfaceContainerLow.takeOrElse { base.sidebarBackground },
        toolbarBackground = surface.takeOrElse { base.toolbarBackground },
        panelBackground = surfaceContainerLowest.takeOrElse { base.panelBackground },
        elevatedBackground = surfaceContainer.takeOrElse { base.elevatedBackground },
        border = outlineVariant.takeOrElse { base.border },
        controlBorder = outline.takeOrElse { base.controlBorder },
        hover = onSurface.takeOrElse { base.onSurface }.copy(alpha = if (dark) DARK_HOVER_ALPHA else LIGHT_HOVER_ALPHA),
        selection = primary.takeOrElse { base.accent }.copy(alpha = if (dark) DARK_SELECTION_ALPHA else LIGHT_SELECTION_ALPHA),
        onSelection = onSurface.takeOrElse { base.onSelection },
        accent = primary.takeOrElse { base.accent },
        onAccent = onPrimary.takeOrElse { base.onAccent },
        accentContainer = primaryContainer.takeOrElse { base.accentContainer },
        onAccentContainer = onPrimaryContainer.takeOrElse { base.onAccentContainer },
        neutralContainer = surfaceContainerHigh.takeOrElse { base.neutralContainer },
        error = error.takeOrElse { base.error },
        onError = onError.takeOrElse { base.onError },
        errorContainer = errorContainer.takeOrElse { base.errorContainer },
        onErrorContainer = onErrorContainer.takeOrElse { base.onErrorContainer },
        info = primary.takeOrElse { base.info },
        onInfo = onPrimary.takeOrElse { base.onInfo },
        infoContainer = primaryContainer.takeOrElse { base.infoContainer },
        onInfoContainer = onPrimaryContainer.takeOrElse { base.onInfoContainer },
        tooltipBackground = inverseSurface.takeOrElse { base.tooltipBackground },
        onTooltip = inverseOnSurface.takeOrElse { base.onTooltip },
        isDark = dark,
    )
}

/**
 * Material's type scale sized like the library's, so Material text in a legacy plugin is not the odd
 * one out. The styles the library has no counterpart for — display, headline, a large title — step
 * up from [JwTextStyles.title] rather than falling back to Material's defaults, which run to 57sp.
 */
private fun materialTypography(styles: JwTextStyles): Typography = Typography(
    displayLarge = styles.title.copy(fontSize = 32.sp, lineHeight = 40.sp),
    displayMedium = styles.title.copy(fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = styles.title.copy(fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = styles.title.copy(fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = styles.title.copy(fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = styles.title.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = styles.title.copy(fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium = styles.title,
    titleSmall = styles.subtitle,
    bodyLarge = styles.body,
    bodyMedium = styles.body,
    bodySmall = styles.bodySmall,
    labelLarge = styles.label,
    labelMedium = styles.label.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = styles.labelSmall,
)

/** Material's shape scale on the library's radii. */
private fun materialShapes(): Shapes = Shapes(
    extraSmall = JwShapes.extraSmall,
    small = JwShapes.small,
    medium = JwShapes.medium,
    large = JwShapes.large,
    extraLarge = JwShapes.large,
)

private const val DISABLED_ALPHA = 0.38f
private const val LIGHT_HOVER_ALPHA = 0.05f
private const val DARK_HOVER_ALPHA = 0.08f
private const val LIGHT_SELECTION_ALPHA = 0.14f
private const val DARK_SELECTION_ALPHA = 0.24f
