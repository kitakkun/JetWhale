package com.kitakkun.jetwhale.host.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** The semantic tone of a tag, dot or banner; each maps to one color family of the theme. */
public enum class JwTone {
    Neutral,
    Accent,
    Success,
    Warning,
    Error,
    Info,
    ;

    /** The strong color of this tone: filled backgrounds, dots, icon tints. */
    public val color: Color
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            Accent -> MaterialTheme.colorScheme.primary
            Success -> JwTheme.colors.success
            Warning -> JwTheme.colors.warning
            Error -> MaterialTheme.colorScheme.error
            Info -> JwTheme.colors.info
        }

    /** Text or icon drawn on top of [color]. */
    public val onColor: Color
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.surface
            Accent -> MaterialTheme.colorScheme.onPrimary
            Success -> JwTheme.colors.onSuccess
            Warning -> JwTheme.colors.onWarning
            Error -> MaterialTheme.colorScheme.onError
            Info -> JwTheme.colors.onInfo
        }

    /** The soft background of this tone: banners, tinted tags. */
    public val containerColor: Color
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.surfaceContainerHigh
            Accent -> MaterialTheme.colorScheme.primaryContainer
            Success -> JwTheme.colors.successContainer
            Warning -> JwTheme.colors.warningContainer
            Error -> MaterialTheme.colorScheme.errorContainer
            Info -> JwTheme.colors.infoContainer
        }

    /** Text or icon drawn on top of [containerColor]. */
    public val onContainerColor: Color
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.onSurface
            Accent -> MaterialTheme.colorScheme.onPrimaryContainer
            Success -> JwTheme.colors.onSuccessContainer
            Warning -> JwTheme.colors.onWarningContainer
            Error -> MaterialTheme.colorScheme.onErrorContainer
            Info -> JwTheme.colors.onInfoContainer
        }
}
