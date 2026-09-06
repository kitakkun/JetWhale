package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** The semantic tone of a tag, dot, banner or button; each maps to one color family of [JwColors]. */
public enum class JwTone {
    Neutral,
    Accent,
    Success,
    Warning,
    Error,
    Info,
    ;

    /** The strong color of this tone: filled backgrounds, dots, icon tints, tag text. */
    public val color: Color
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            Neutral -> JwTheme.colors.textSecondary
            Accent -> JwTheme.colors.accent
            Success -> JwTheme.colors.success
            Warning -> JwTheme.colors.warning
            Error -> JwTheme.colors.error
            Info -> JwTheme.colors.info
        }

    /** Text or icon drawn on top of [color]. */
    public val onColor: Color
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            Neutral -> JwTheme.colors.surface
            Accent -> JwTheme.colors.onAccent
            Success -> JwTheme.colors.onSuccess
            Warning -> JwTheme.colors.onWarning
            Error -> JwTheme.colors.onError
            Info -> JwTheme.colors.onInfo
        }

    /** The soft background of this tone: banners, tinted tags. */
    public val containerColor: Color
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            Neutral -> JwTheme.colors.neutralContainer
            Accent -> JwTheme.colors.accentContainer
            Success -> JwTheme.colors.successContainer
            Warning -> JwTheme.colors.warningContainer
            Error -> JwTheme.colors.errorContainer
            Info -> JwTheme.colors.infoContainer
        }

    /** Text or icon drawn on top of [containerColor]. */
    public val onContainerColor: Color
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            Neutral -> JwTheme.colors.onSurface
            Accent -> JwTheme.colors.onAccentContainer
            Success -> JwTheme.colors.onSuccessContainer
            Warning -> JwTheme.colors.onWarningContainer
            Error -> JwTheme.colors.onErrorContainer
            Info -> JwTheme.colors.onInfoContainer
        }
}
